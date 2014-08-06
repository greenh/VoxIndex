/*
 *  Copyright (c) 2011 - 2014 Howard Green. All rights reserved.
 *  The use and distribution terms for this software are covered by the
 *  Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 *  which can be found in the file epl-v10.html at the root of this distribution.
 *  By using this software in any fashion, you are agreeing to be bound by
 *  the terms of this license.
 *  
 *  You must not remove this notice, or any other, from this software.
 */
package voxindex.audiology;

import java.util.Arrays;
import java.util.Set;

import voxindex.shared.LoginRequiredException;
import voxindex.shared.LookupResult;
import voxindex.shared.VoxIndexService;

import voxindex.audiology.RPCSession.RPCRequest;
import android.media.AudioRecord;
import android.media.MediaRecorder.AudioSource;
import android.util.Log;
import static android.media.AudioFormat.*;

/** 
 * Acquires potential phrases from an incoming audio stream.
 * 
 * Audiator is in essence a wrapper for its internal AReader class,
 * which runs as a separate thread, and does the actual work of 
 * extracting phrase candidates from the incoming audio stream.
 */
public class Audiator {

	public static final String Tag = "Audiator"; 
	
	public static final int waveBytes = 44;	// WAVE header bytes

	/**
	 * Converts a period in milliseconds into an (integer) frequency in Hz.
	 * @param msec Period, in msec.
	 * @return Frequencey, in Hz.
	 */
	public static int msecFreq(int msec) { return 1000/msec; }

	/*
	 * Audio parameters that are (at least in principle) tuneable.
	 * Note, however, that this is more or less hardwired for use with a 
	 * mono PCM-16 sample stream, with rate set below. 
	 */
	/** Bits per audio sample. */
	final int sampleSize = 16;
	/** Audio data samples per second. */
	final int sampleRate = 16000;
	/** Number of milliseconds received at a time */
	final int chunkMSec = 100;	
	/** Max milliseconds of a phrase */
	final int maxVoxMSec = 5000;
	/** Maximum sample value interpreted as "silence". */
	int silenceThreshold = 300;
	/** Duration of non-silence needed to conclude that speech has started */
	final int newVoxMSec = 400;
	/** Duration of silence needed to conclude that speech has stopped */
	final int tailSilenceMSec = 700;
	/** Amount of tail-end silence to discard prior to transmit. */
	final int tailSilenceChopMSec = 500;
	final int devoxBreakMSec = 1000;
	
	/*
	 * Derived parameters
	 */
	final int sampleBytes = (sampleSize + 7) / 8;	
	final int chunkSamples = (sampleRate / msecFreq(chunkMSec));
	final int chunkBytes = (chunkSamples * sampleBytes);
	
	final int newVoxChunks = (newVoxMSec / chunkMSec);
	final int maxVoxChunks = (maxVoxMSec / chunkMSec);
	final int tailSilenceChunks = (tailSilenceMSec  /chunkMSec);
	final int tailSilenceChopChunks = (tailSilenceChopMSec / chunkMSec);
		
	final int devoxBreakChunks = (devoxBreakMSec / chunkMSec);
	
	private final Audiology audiology;
	private final AReader reader;
	private final AudioRecord recorder;
	
	private String tag = null;
	private Set<String> indexIDs = null;
	
	private byte[] lastWave = null;


	public Audiator(Audiology audiology) {
		this.audiology = audiology;
		
		int bufferSize = sampleRate * 5 * sampleBytes; // 5-second buffer
		
		recorder = new AudioRecord(AudioSource.VOICE_RECOGNITION, sampleRate, CHANNEL_IN_MONO, 
								ENCODING_PCM_16BIT, bufferSize);
		if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
			Log.w(Tag, "Audio didn't initialize");
		else
			Log.v(Tag, "Audiator initialized");
		reader = new AReader();
		reader.start();
	}	// Audiator
	
	/**
	 * Sets contextual information that is to be used when making a request.
	 * @param tag The identification tag of the requesting context.
	 * <p>Normally, this is the tag of tab context (or other ID); it's attached
	 * to the request, and returned by the server in the response. 
	 * @param indexIDs A set of index ID strings. These are supplied to the server
	 * as part of a lookup request. 
	 */
	public void setRequestContext(String tag, Set<String> indexIDs) {
		synchronized(this) {
			this.tag = tag;
			this.indexIDs = indexIDs;
		}
	}
	
	public void setSilenceThreshold(int silenceThreshold) { 
		this.silenceThreshold = silenceThreshold;
	}
	
	/**
	 * Starts the phrase acquisition process.
	 */
	public void start() {
		reader.startReading();
	}
	
	/**
	 * Stops the phrase acquisition process.
	 */
	public void stop() {
		reader.stopReading();
	}	
	
	public byte[] getLastWave() { return lastWave; }
	
	private enum VoxState { Idle, Silence, PossiblyVox, Voxing, 
		MightBeSilence, Devoxing };

	/**
	 * Audio data acquisition thread. 
	 * <p>Upon being signalled to start, an AReader continuously synchronously
	 * retrieves chunks of audio data from the Android AudioRecorder thingy. 
	 * When it's got a chunk, it examines it to determine if there's enough
	 * received signal to justify a conclusion that the chunk is other than
	 * "silence". If so, and if enough such signal-bearing chunks are acquired,
	 * the AReader concludes that it has a candidate spoken phrase. It deems 
	 * the phrase to have ended when enough it detects a sufficient number of chunks of silence, where  
	 */
	private class AReader extends Thread {
		
		private byte[] buffer = new byte[sampleRate * sampleBytes * 8];
		private boolean terminated = false;
		private boolean started = false;
		private boolean stopped = false;

		public AReader() {
			insertString(buffer, 0, "RIFF");	// 'RIFF' 
//			insertInt(buffer, 4, data-size + 36);   // total chunksize
			insertString(buffer, 8, "WAVE");  	// 'WAVE' 
			insertString(buffer, 12, "fmt ");	// format chunk header
			insertInt(buffer, 16, 16);			// format chunk size
			insertShort(buffer, 20, 1);			// PCM format code
			insertShort(buffer, 22, 1);			// nr. of channels = 1
			insertInt(buffer, 24, sampleRate);	// samples/sec
			insertInt(buffer, 28, sampleRate * sampleBytes); // data rate
			insertShort(buffer, 32, sampleBytes);		// data block size
			insertShort(buffer, 34, sampleBytes * 8);	// bits/sample
			insertString(buffer, 36, "data");	// data chunk header
// 			insertString(buffer, 40, data-size);		// data size
// 			~~~ data follows ~~~
		}
		
		public void startReading() {
			synchronized(this) {
				started = true;
				notify();
			}
			Log.d(Tag, "Reader start received");
		}
		
		@SuppressWarnings("unused") public void terminate() {
			terminated = true;
		}	// terminate
		
		public void stopReading() {
			stopped = true;
			Log.d(Tag, "Reader stop received");
		}
		private int chunkOffset(int chunk) {
			return chunk * (chunkSamples * sampleBytes) + waveBytes;
		}
		
		private int toInt(byte ls, byte ms) {
			return (((int) ls) & 0xff) | (((int) ms) << 8);
		}
		
		/**
		 * Reads the next chunk's worth of audio data into the indicated
		 * chunk in the buffer, and checks it out to see if there's some 
		 * indication that it might contain voice signal.
		 * @param chunk Chunk index in the current buffer to read into
		 * @return True if the received chunk meets the definition of "silence"
		 * @throws Exception Something went off the wire, or so to speak...
		 */
		private boolean nextChunkIsSilence(int chunk) throws Exception {
			int sc = recorder.read(buffer, chunkOffset(chunk), chunkBytes);
			if (sc != chunkBytes)
				throw new Exception("Expecting " + chunkSamples + ", got " + sc);
			int base = chunkOffset(chunk);
			for (int i = 0; i < chunkBytes; i += sampleBytes) {
				int val = toInt(buffer[base + i], buffer[base + i + 1]); 
				if (Math.abs(val) > silenceThreshold) 
					return false;
			}
			return true;
		}	// nextChunkIsSilence
		
		class Range {
			int min = Integer.MAX_VALUE;
			int max = Integer.MIN_VALUE;
		}
		
		private Range valueRange(int chunks) {
			Range range = new Range();
			
			for (int i = 0; i < chunks * chunkBytes; i += sampleBytes) {
				int val = toInt(buffer[i], buffer[i + 1]); 
				range.min = Math.min(range.min, val);
				range.max = Math.max(range.max, val);
			}
			return range;
		}
		
		private boolean stopCheck() {
			if (stopped) {
				recorder.stop();
				audiology.signalIdle();
				Log.v(Tag, "AReader recording stopped");
				return true;
			}
			return false;
		}	// stopCheck
		
		@Override public void run() {
			Log.v(Tag, "AReader running");
			try {
				int chunk = 0;
				int silenceBase = 0;
				VoxState vstate = VoxState.Idle;

				while (! terminated) {
					switch (vstate) {
					case Idle:
						synchronized(this) {
							started = false;	// toss any previous arrivals
							while ((! started) && (! terminated)) {
								try {
									wait();
								} catch (InterruptedException e) { }
							stopped = false;
							}
						}
						if (! terminated) {
							recorder.startRecording();
							chunk = 0;
							if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
								Log.v(Tag, "AReader recording started");
								audiology.signalRecording();
								vstate = VoxState.Silence;
							} else {
								Log.w(Tag, "AReader recording failed to start");
								audiology.signalFailed();
							}
						} break;
					
					case Silence:
						if (stopCheck()) {
							vstate = VoxState.Idle;
						} else if (nextChunkIsSilence(chunk))
							;
						else {
							chunk++;
							vstate = VoxState.PossiblyVox;
						} break;
						
					case PossiblyVox:
						if (stopCheck()) {
							vstate = VoxState.Idle;
						} else if (nextChunkIsSilence(chunk)) {
							chunk = 0;
							vstate = VoxState.Silence;
						} else {
							chunk++;
							if (chunk >= newVoxChunks) {
								audiology.signalVoiceDetected();
								vstate = VoxState.Voxing;
							} else
								;	// we're good.
						} break;
							
					case Voxing:
						if (stopCheck()) {
							vstate = VoxState.Idle;
						} else if (nextChunkIsSilence(chunk)) {
							silenceBase = chunk;
							chunk++;
							vstate = VoxState.MightBeSilence;
						} else {
							chunk++;
							if (chunk >= maxVoxChunks) {
								chunk = 0;
								Range r = valueRange(maxVoxChunks);
								Log.d(Tag, "Voice overflow: min " + r.min
										+ " max " + r.max);
								audiology.signalVoiceOverflow();
								vstate = VoxState.Devoxing;
							} else
								;	// also good
						} break;
						
					case MightBeSilence:
						if (stopCheck()) {
							vstate = VoxState.Idle;
						} else if (nextChunkIsSilence(chunk)) {
							chunk++;
							if (chunk - silenceBase > tailSilenceChunks) {
								audiology.signalVoiceAcquired();
								transferData(buffer, 
										(chunk - tailSilenceChopChunks) * chunkBytes);
								// recorder.stop();
								chunk = 0;
								vstate = VoxState.Silence;
							} else
								;
						} else {
							chunk++;
							if (chunk >= maxVoxChunks) {
								chunk = 0;
								silenceBase = 0;
								Range r = valueRange(maxVoxChunks);
								Log.d(Tag, "Voice overflow: min " + r.min
										+ " max " + r.max);audiology.signalVoiceOverflow();
								vstate = VoxState.Devoxing;
							} else {
								vstate = VoxState.Voxing;
							}
						} break;
						
					case Devoxing:
						if (stopCheck()) {
							vstate = VoxState.Idle;
						} else if (nextChunkIsSilence(chunk)) {
							silenceBase++;
							if (silenceBase > devoxBreakChunks) {
								// audiology.signalIdle();
								chunk = 0;
								vstate = VoxState.Silence;
							} else 
								;
						} else {
							silenceBase = 0; // take that!!
						} break;
							
					default:
						Log.e(Tag, "AReader Bad awful state detected");
					}
				}
				Log.v(Tag, "AReader terminated");
			} catch (Exception e) {
				Log.e(Tag, "AReader exception occured", e);
			} finally {
				recorder.stop();
				audiology.signalIdle();
			}
		}	// run
	}	// AReader
	
	private void insertInt(byte[] b, int n, int v) {
		b[n + 0] = (byte) ((v << 24) >> 24);
		b[n + 1] = (byte) ((v << 16) >> 24);
		b[n + 2] = (byte) ((v << 8) >> 24);
		b[n + 3] = (byte) (v >> 24);
	}
	private void insertShort(byte[] b, int n, int v) {
		b[n + 0] = (byte) ((v << 24) >> 24);
		b[n + 1] = (byte) ((v << 16) >> 24);
	}
	private void insertString(byte[] b, int n, String s) {
		for (int i = 0; i < s.length(); i++)
			b[n + i] = (byte) s.charAt(i);
	}

	/**
	 * Sends a received candidate vocal phrase's audio data off to valhalla by way
	 * of the session services.  
	 * @param buffer The buffer to send. This is assumed to have a 44-byte WAVE header
	 * at its start.
	 * @param bytes The amount of audio data in the buffer in bytes, <b>not</b> 
	 * including the WAVE header. 
	 */
	public void transferData(byte[] buffer, int bytes) { 
		msg("Acquired " + bytes + " = " 
				+ (((float) bytes) / (float) (sampleRate * sampleBytes)) + " sec." ); 
		insertInt(buffer, 4, bytes + 36);   // total chunksize
		insertInt(buffer, 40, bytes);		// data size
		final Set<String> iids;
		final String t;
		synchronized(this) {
			iids = indexIDs;
			t = tag;
		}
		
		lastWave = Arrays.copyOf(buffer, bytes + waveBytes);
		
		audiology.getSession().doRPCRequest(
				new RPCRequest(){
					@Override public void request(VoxIndexService service, String sessionID) 
																	throws LoginRequiredException {
						Log.v(Tag, "VoxLookup " + lastWave.length + " bytes");
//						if (audiology.getHexDump()) {
//							String s = "VoxRequestor clip content:\n";
//							for (int i = 0; i < 256; i++) {
//								if ((i % 16) == 0)
//									s += String.format("%04x: ", i);
//								s += String.format("%02x ", clip[i]);
//								if ((i % 16) == 15)
//									s += "\n";
//							}
//							message(s);
//						}
						LookupResult rslt = service.voxLookup(sessionID, t, iids, lastWave);
						audiology.lookupResult(rslt);
					}

					@Override public void failed(Throwable t) {
						Log.e(Tag, "VoxLookup exception: ", t);
						audiology.requestException(t);
					}});
	}	// transferData
	
	public void msg(String message) {
		audiology.sendDisplayMessage(message);
	}
	

}
