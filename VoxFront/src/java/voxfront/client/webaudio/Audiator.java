package voxfront.client.webaudio;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.typedarrays.client.ArrayBufferNative;
import com.google.gwt.typedarrays.client.DataViewNative;
import com.google.gwt.typedarrays.client.Int16ArrayNative;
import com.google.gwt.typedarrays.shared.DataView;
import com.google.gwt.typedarrays.shared.Float32Array;
import com.google.gwt.typedarrays.shared.Int16Array;
import com.google.gwt.webworker.client.Worker;
import com.google.gwt.webworker.client.ErrorEvent;
import com.google.gwt.webworker.client.ErrorHandler;
import com.google.gwt.webworker.client.MessageEvent;
import com.google.gwt.webworker.client.MessageHandler;

import java.util.logging.Level;
import java.util.logging.Logger;

import voxfront.client.VoxFront;
import voxfront.util.Map;
import voxfront.util.Op;
import elemental.html.AudioContext;
import elemental.dom.LocalMediaStream;
import elemental.dom.MediaStreamSourcesCallback;
import elemental.dom.SourceInfo;
import elemental.events.Event;
import elemental.events.EventListener;
import elemental.html.AudioBuffer;
import elemental.html.AudioProcessingEvent;
import elemental.html.AudioProcessingHandler;
//import elemental.html.Float32Array;
import elemental.html.MediaStreamAudioSourceNode;
import elemental.html.Navigator;
import elemental.html.NavigatorUserMediaError;
import elemental.html.NavigatorUserMediaSuccessCallback;
import elemental.html.NavigatorUserMediaErrorCallback;
import elemental.html.ScriptProcessorNode;
import elemental.html.Window;
import elemental.html.AudioGainNode;
import elemental.js.dom.JsSourceInfo;
import elemental.client.Browser;
import elemental.util.Mappable;


public class Audiator {
	private static Logger logger = Logger.getLogger("VoxFront/Audiator");

	
	public interface InitializeCallback {
		public void onSuccess();
		public void onFailure(int code);
	}
	
	public interface CompletionCallback {
		public void deliverWAV(DataView blob);
	}
	
	public static native void getMediaStreamTrackSources(MediaStreamSourcesCallback callback) /*-{
		return MediaStreamTrack.getSources(
			$entry(callback.@elemental.dom.MediaStreamSourcesCallback::onSource(Lelemental/dom/SourceInfo;)).bind(callback));
	}-*/;
	
	public static native void getSourcesB() /*-{
		MediaStreamTrack.getSources(function(sis) { 
    	sis.forEach(function(si) { 
       	console.log(si.kind + ' ' + si.label + ' ' + si.id); 
    	});
 		})
	}-*/;

	
	public static native Mappable getOptsXX() /*-{ return {audio: true}; }-*/;
	
	AudioContext audioContext = null;
	private Worker audi;
	final VoxFront ui;
	private boolean audiating = false;
	
	ScriptProcessorNode sNode;
	
	final float silenceThreshold = 0.022f;
	/** Samples per delivered buffer */
	final int chunkSamples = 2048;  // must be power of 2
	/** Samples per second */
	float sampleRate;
	/** Time represneted by one chunk, in msec. */
	float chunkMSec;		
	
	
	int apEvents = 0;
	
	
	public Audiator(VoxFront ui) {
		this.ui = ui;
	}
	
	private static native void conlog(String what) /*-{
		console.log('Audiator: ' + what);
	}-*/;
	
//	public static native void zap(ScriptProcessorNode node) /*-{
//		node.onaudioprocess = function(e){
//			console.log('Got an audio event');
//		}
//	}-*/;
	
	public void initialize() {
		logger.log(Level.INFO, "starting initialize");
		audi = Worker.create("../audiator/audiator.nocache.js");
		audi.setOnError(new ErrorHandler() {
			@Override public void onError(ErrorEvent event) {
				audiatorError(event);
			}
		});
		audi.setOnMessage(new MessageHandler() {
			@Override public void onMessage(MessageEvent event) {
				audiatorResponse(event);
			}
		});
		
		logger.log(Level.INFO, "starting source query");
		getSourcesB();
//		getMediaStreamTrackSources(new MediaStreamSourcesCallback() {
//			@Override public void onSource(SourceInfo info) {
//				logger.log(Level.INFO, "source: " + info.getKind() 
//										+ " " + info.getType() + " " + info.getSourceId());
//			}});
		logger.log(Level.INFO, "finished");
		
		Window window = Browser.getWindow();
		audioContext = window.newAudioContext();
		logger.log(Level.INFO, "acquired audioContext " + audioContext);
		Navigator navigator = window.getNavigator();
		Mappable opts = getOptsXX();
		
		
		navigator.webkitGetUserMedia(opts, 
			new NavigatorUserMediaSuccessCallback() {
				@Override public boolean onNavigatorUserMediaSuccessCallback(LocalMediaStream stream) {
					
					
					
					return false;
					}},
			new NavigatorUserMediaErrorCallback() {
				@Override public boolean onNavigatorUserMediaErrorCallback(NavigatorUserMediaError error) {
					logger.log(Level.INFO, "media stream acquisition failed -- " + error.getCode());
					ui.audiatorFailed(error.getCode());
					return false;
				}				
			});
		
		getSourcesB();
	
		
		navigator.webkitGetUserMedia(opts, 
			new NavigatorUserMediaSuccessCallback() {
				@Override public boolean onNavigatorUserMediaSuccessCallback(LocalMediaStream stream) {
					
					getSourcesB();
					
					logger.log(Level.INFO, "acquired media stream -- " + stream);
					MediaStreamAudioSourceNode input = audioContext.createMediaStreamSource(stream);
					// input.connect(audioContext.getDestination());
					sNode = audioContext.createScriptProcessor(chunkSamples);
					sNode.setOnaudioprocess(new AudioProcessingHandler() {
						@Override public void onAudioProcessing(AudioProcessingEvent ev) {
							processAudio(ev);

						}});
					input.connect(sNode);
					sNode.connect(audioContext.getDestination());
					sampleRate = audioContext.getSampleRate();
					chunkMSec = (((float) chunkSamples) / sampleRate) * 1000;
					logger.log(Level.INFO, "chunk size " + chunkSamples + " samples; "
										+ "sample rate " + sampleRate + " per sec; "
										+ " chunk msec " + chunkMSec);
					int sr = (int) sampleRate;
					int cs = (int) chunkSamples;
					audi.postMessage(
							Op.makeOp("config")
								.put("rate", sr)
								.put("chunk", cs)
								.put("silence", silenceThreshold));	
					ui.audiatorInitialized();	
					return false;
					}},
			new NavigatorUserMediaErrorCallback() {
				@Override public boolean onNavigatorUserMediaErrorCallback(NavigatorUserMediaError error) {
					logger.log(Level.INFO, "media stream acquisition failed -- " + error.getCode());
					ui.audiatorFailed(error.getCode());
					return false;
				}				
			});
	}	// initialize
		
	public void start() {
		logger.log(Level.INFO, "Tracking starting (" + audiating + ")");
		if (!audiating) {
			audiating = true;
			audi.postMessage(Op.makeOp("start"));
			ui.audiatorRunning();
		}
	}	// start
	
	public void stop() { 
		logger.log(Level.INFO, "Tracking stopping (" + audiating + ")");
		if (audiating) {
			audi.postMessage(Op.makeOp("stop"));
			audiating = false;
			ui.audiatorStopped();
		}
	}	// stop
	
	/**
	 * Handles a chunk of audio data.
	 * @param ev The AudioProcessingEvent containing the input data.
	 */
	public void processAudio(AudioProcessingEvent ev) {
		apEvents++;
//		if ((apEvents % 100) == 1) {
//			logger.log(Level.INFO, "got " + apEvents + " audio events");
//		}	
		if (audiating) {
			AudioBuffer b = ev.getInputBuffer();
			Float32Array data = b.getChannelData(0);
			audi.postMessage(Op.makeOp("data").put("data", data));
		} // else, just toss the event
	}	// processAudio
	
	/**
	 * Respond to a message posted by the audiator worker.
	 * @param event The event generated by the message posting.
	 */
	void audiatorResponse(MessageEvent event) {
		Op resp = event.getData().cast();
		String op = resp.getOp();
		if (op == "status") {
			String status = resp.getString("status");
			ui.audiatorStatus(status);
		} else if (op == "wave") {
			ArrayBufferNative wave = resp.get("data");
			DataView dv = DataViewNative.create(wave);
			int bytes = resp.getInt("size");
			logger.log(Level.INFO, "----------------------- got wave data, " + bytes);
			logger.log(Level.INFO, "wave length: " + dv.getInt32(4, true));
			ui.deliverWave(wave, bytes);
		} else {
			logger.log(Level.INFO, "Audiator response: " + op);
		}
	}
 
	/**
	 * Respond to an error generated in the audiator worker.
	 * @param event
	 */
	void audiatorError(ErrorEvent event) {
		logger.log(Level.SEVERE, "audiator reports error: \"" + event.getMessage() 
					+ "\" (" + event.getFilename() + ":" + event.getLineNumber() + ")" );

	}

	
}
