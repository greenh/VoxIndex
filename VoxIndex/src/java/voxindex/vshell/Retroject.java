package voxindex.vshell;

import system.speech.recognition.Grammar;
import system.speech.recognition.GrammarBuilder;
import system.speech.recognition.SemanticResultKey;

public class Retroject {
	
	public static Grammar toGrammar(GrammarBuilder gb) {
		return new Grammar(gb);
	}
	

}
