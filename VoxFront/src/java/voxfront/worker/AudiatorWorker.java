package voxfront.worker;

import voxfront.util.Op;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.typedarrays.client.ArrayBufferNative;
import com.google.gwt.typedarrays.client.DataViewNative;
import com.google.gwt.typedarrays.client.Float32ArrayNative;
import com.google.gwt.typedarrays.client.Int16ArrayNative;
import com.google.gwt.typedarrays.shared.DataView;
import com.google.gwt.typedarrays.shared.Float32Array;
import com.google.gwt.typedarrays.shared.Int16Array;
import com.google.gwt.webworker.client.DedicatedWorkerEntryPoint;
import com.google.gwt.webworker.client.MessageEvent;
import com.google.gwt.webworker.client.MessageHandler;

import static voxfront.worker.AudiatorWorker.VoxState.*;
import static voxfront.worker.AudiatorWorker.AudiatorStatus.*;
import static voxfront.worker.AudiatorWorker.AudiatorEvent.*;

public class AudiatorWorker extends DedicatedWorkerEntryPoint implements MessageHandler {

	
	// Parameters for processing the audio stream, all in msec. 
	/** Max milliseconds of a phrase */
	final int maxVoxMSec = 5000;
	/** Duration of non-silence needed to conclude that speech has started */
	final int newVoxMSec = 300;
	/** Duration of silence needed to conclude that speech has stopped */
	final int tailSilenceMSec = 700;
	/** Amount of tail-end silence to discard prior to transmit. */
	final int tailSilenceChopMSec = 500;
	/** Amount of silence needed to reset after overflow */
	final int devoxBreakMSec = 1000;
	/** Amount of silence tolerated after inital signal */
	final int interSilenceMSec = 250;
	
	final int sampleBytes = 2; 	// note that this is the FINAL sample size!!

	// Parameters set from config information
	/** Samples per second */
	int sampleRate = 48000;
	/** Samples per delivered buffer */
	int chunkSamples = 1024;  // must be power of 2
	/** Maximum sample value interpreted as "silence". */
	float silenceThreshold = 0.02f;
	
	// Derived parameters
	/** Time represneted by one chunk, in msec. */
	double chunkMSec;		
	
	/** min number of non-silent chunks to be considered significant */
	int minVoxChunks;
	/** max number of chunks in a phrase */
	int maxVoxChunks;
	/** number of silence chunks to indicate end-of-voice */
	int tailSilenceChunks;
	/** number of chunks of silence required to establish post-overflow quiet */
	int devoxingChunks;
	/** number of chunks of silence to chop off a completed phrase */
	int tailSilenceChopChunks;
	/** number of chunks of silence within a phrase */
	int interSilenceChunks;
	
	
	// Values of the enum below have to match those in voxfront.client.AudiatorStatus!!!
	public enum AudiatorStatus { Idle, Quiet, SignalDetected, Overflow };
	
	public enum VoxState { Inactive, Silence, PossiblyVox, Voxing, MightBeTheEnd, Devoxing };
	
	private VoxState vstate = VoxState.Inactive;
	float maxval;
	float minval;
	int frames;
	
	// events for the state machine
	public enum AudiatorEvent { Start, Stop, Data };
	
	
	public AudiatorWorker() { 	}

	
	private native void conlog(String what) /*-{
		console.log('AudiatorWorker: ' + what);
	}-*/;
	
	/**
	 * Worker entry point.
	 */
	@Override public void onWorkerLoad() {
		conlog("It's alive! Really!");
		setOnMessage(this);
	}
	
	/**
	 * Message event handler. 
	 * <p>Messages posted by the main thread appear here.
	 */
	@Override public void onMessage(MessageEvent event) {
		Op req = event.getData().cast();
		String op = req.getOp();
		// conlog("Received op: " + op);
		
		if (op == "data") {
			Float32ArrayNative samples = req.get("data");
			processIncoming(samples);
		} else if (op == "config") {
			configure(req);
			getNewBuffers();
		} else if (op == "start") {
			maxval = Float.MIN_VALUE;
			minval = Float.MAX_VALUE;
			frames = 0;
			transition(Start);
//			vstate = VoxState.Silence;	// yecch.
//			statusUpdate(AudiatorStatus.Quiet);
//			postMessage(Op.makeOp("started"));
		} else if (op == "stop") {
			conlog("processed " + frames + ", max " + maxval + ", min " + minval);
			transition(Stop);
//			vstate = VoxState.Inactive;	// yecch.
//			statusUpdate(AudiatorStatus.Idle);
//			postMessage(Op.makeOp("stopped"));
		}
	}

	/**
	 * Accepts a map of exogenous parameters supplied by the window thread, based
	 * on user- or input-stream-specified values, and uses these to
	 * set a flock of derived parameters used by the audio processing process, 
	 * @param req A JavaScript map of name/value pairs.
	 */
	void configure(Op req) {
		sampleRate = req.getInt("rate");
		chunkSamples = req.getInt("chunk");
		silenceThreshold = req.getFloat("silence");
		chunkMSec = (1000.0 * ((double) chunkSamples) / ((double) sampleRate));
		minVoxChunks = (int) (((double) newVoxMSec) / chunkMSec);
		maxVoxChunks = (int) (((double) maxVoxMSec) / chunkMSec);
		interSilenceChunks = (int) (((double) interSilenceMSec) / chunkMSec);
		tailSilenceChunks = (int) (((double) tailSilenceMSec) / chunkMSec);
		devoxingChunks = (int) (((double) devoxBreakMSec) / chunkMSec);
		conlog("config --"
				+ " rate: " + sampleRate 
				+ " chunk: " + chunkSamples
				+ " chunk ms: " + chunkMSec
				+ " min: " + minVoxChunks
				+ " max: " + maxVoxChunks
				+ " inter: " + interSilenceChunks
				+ " tail: " + tailSilenceChunks
				+ " devox: " + devoxingChunks
				+ " silence: " + silenceThreshold);
	}	// configure
	
	void statusUpdate(AudiatorStatus status) {
		postMessage(Op.makeOp("status").put("status", status.toString()));
	}
	
	static final int waveHeaderBytes = 44;
	
	ArrayBufferNative abuffer;
	Int16Array voxdata;
	
	void insertString(DataView dv, int off, String s) {
		for (int i = 0; i < s.length(); i++)
			dv.setUint8(off + i, s.charAt(i));
	}
	
	/**
	 * Generates a new buffer capable of handling a max-sized phrase. 
	 * <p>We do the
	 * putative normal JavaScript thing of allocating for the typical element
	 * size (16-bit integers in this case), and then 
	 */
	void getNewBuffers() {
		int bytes = waveHeaderBytes + maxVoxChunks * chunkSamples * sampleBytes;
		conlog("new buffer " + bytes + " bytes");
		abuffer = ArrayBufferNative.create(bytes);
		voxdata = Int16ArrayNative.create(abuffer, waveHeaderBytes);
	}	// getNewBuffers
	
	private int chunk = 0;
	private int silence = 0;
	private boolean isQuiet;
	
	/**
	 * Processes the incoming stream of "chunks", or buffers of audio samples.
	 * <p>processIncoming is invoked in response to receipt of a chunk 
	 * of audio data. It scrutinizes the received chunk
	 * to see if it contains samples with large enough
	 * amplitude to plausibly be anything other than background noise. 
	 * <p>Once enough non-silent data has been recorded to plausibly be a real
	 * voiced phrase, processIncoming starts looking for a period of silence 
	 * as a way of representing the end of the phrase. Once this appears, the 
	 * phrase is packaged up as a WAVE format object, and returned to the UI
	 * for downstream processing.
	 * <p>If non-silent input goes on too long, processIncoming declares an overflow
	 * condition, and simply tosses incoming chunks until it gets some peace and quiet.
	 * @param samples The latest array of audio sample value.
	 */
	void processIncoming(Float32Array samples) {
		float magmax = 0;
		int chunkOff = chunk * chunkSamples;
		frames++;
		for (int i = 0; i < samples.length(); i++) {
			float sample = samples.get(i);
			magmax = Math.max(magmax, Math.abs(sample));
			float s = Math.max(-1, Math.min(1, sample));
			voxdata.set(chunkOff + i, (short) (s < 0 ? (-s * Short.MIN_VALUE) : (s * Short.MAX_VALUE)));
			
			// Note that these are cumulative over time, and not frame/phrase specific
			maxval = Math.max(maxval, sample);
			minval = Math.min(minval, sample);
		}
		isQuiet = magmax < silenceThreshold;
		transition(Data);
	}
	
	private void badTransition(VoxState vstate, AudiatorEvent ev) {
		conlog("Bad transition: " + vstate + "." + ev);	
	}
	
	private void newState(VoxState s) { vstate = s; }
	
	
	void transition(AudiatorEvent event) {
		switch (vstate) {
		case Inactive:
			switch (event) {
			case Start:
				getNewBuffers();
				chunk = 0;
				statusUpdate(AudiatorStatus.Quiet);
				newState(Silence);
				break;
			case Stop:
			case Data:
				break;
			default: badTransition(vstate, event);
			} break;
			
		case Silence:
			switch (event) {
			case Start:
				break;
			case Stop:
				statusUpdate(Idle);
				newState(Inactive);
				break;
			case Data:
				if (isQuiet)
					newState(Silence);
				else {
					conlog("signal");
					chunk++;
					silence = 0;
					newState(PossiblyVox);
				} 
				break;
			default: badTransition(vstate, event);
			} break;
			
		case PossiblyVox:
			switch (event) {
			case Start:
				break;
			case Stop:
				statusUpdate(Idle);
				newState(Inactive);
				break;
			case Data:
				chunk++;
				if (isQuiet) {
					silence++;
					if (silence > interSilenceChunks) {
						chunk = 0;
						conlog("went quiet");
						newState(Silence);
					} else {
						newState(PossiblyVox);
					}
				} else {
					silence = 0;
					if (chunk >= minVoxChunks) {
						conlog("got vox");
						statusUpdate(AudiatorStatus.SignalDetected);
						newState(Voxing);
					} else
						newState(PossiblyVox);
				} 
				break;
			default: badTransition(vstate, event);
			} break;
			 
		case Voxing:
			switch (event) {
			case Start:
				break;
			case Stop:
				statusUpdate(Idle);
				newState(Inactive);
				break;
			case Data:
				chunk++;
				if (chunk >= maxVoxChunks) {
					silence = 0;
					statusUpdate(Overflow);
					newState(Devoxing);
					conlog("audio overflow");
				} else if (isQuiet) {
					silence = chunk;
					newState(MightBeTheEnd);
				} else
					newState(Voxing);
				break;
			default: badTransition(vstate, event);
			} break;
			
		case MightBeTheEnd:
			switch (event) {
			case Start:
				break;
			case Stop:
				// Should we save in-progress data on Stop, or toss it?
				// We could add a cancel event... :-)
				transferData(chunk);
				chunk = 0;
				statusUpdate(Idle);
				newState(Inactive);
				break;
			case Data:
				chunk++;
				if (chunk >= maxVoxChunks) {
					statusUpdate(Overflow);
					vstate = Devoxing;
					conlog("audio overflow");
				} else if (isQuiet) {
					if (chunk - silence > tailSilenceChunks) {
						conlog("end detect");
						transferData(chunk - tailSilenceChopChunks);
						statusUpdate(Quiet);
						getNewBuffers();
						chunk = 0;
						newState(Silence);
					} else 
						newState(MightBeTheEnd);
				} else
					newState(Voxing);
				break;
			default: badTransition(vstate, event);
			} break;
			
		case Devoxing:
			switch (event) {
			case Start:
				break;
			case Stop:
				statusUpdate(Idle);
				newState(Inactive);
				break;
			case Data:
				if (isQuiet) {
					silence++;
					if (silence > devoxingChunks) {
						statusUpdate(Quiet);
						chunk = 0;
						newState(Silence);
					} else
						newState(Devoxing);
				} else {
					newState(Devoxing);
				} break;
			
			default: badTransition(vstate, event);
			} break;
				
		default:
			conlog("Bad awful evil state detected");
		}
	}	// transition
	
	/**
	 * Packages up received audio data into a nice WAV format object, and
	 * returns it to the UI.
	 * @param chunks Number of chunks in the buffer
	 */
	void transferData(int chunks) {
		DataView dv = DataViewNative.create(abuffer);
		int dataSize = chunkSamples * sampleBytes * chunks;
		 
		insertString(dv, 0, "RIFF");	// 'RIFF' 
		dv.setUint32(4, dataSize + 32, true);   // total chunksize
		insertString(dv, 8, "WAVE");  	// 'WAVE' 
		insertString(dv, 12, "fmt ");	// format chunk header
		dv.setUint32(16, 16, true);			// format chunk size
		dv.setUint16(20, 1, true);			// PCM format code
		dv.setUint16(22, 1, true);			// nr. of channels = 1
		dv.setUint32(24, sampleRate, true);	// samples/sec
		dv.setUint32(28, sampleRate * sampleBytes, true); // data rate
		dv.setUint16(32, sampleBytes, true);		// data block size
		dv.setUint16(34, sampleBytes * 8, true);	// bits/sample
		insertString(dv,36, "data");	// data chunk header
		dv.setUint32(40, dataSize, true);		// data size
// 	~~~ data follows ~~~		
		
		conlog("Sending wave ( " + (dataSize + 32) + " bytes)");
		postMessage(Op.makeOp("wave").put("size", dataSize + 32).put("data", abuffer), abuffer);
	}	// transferData

}
