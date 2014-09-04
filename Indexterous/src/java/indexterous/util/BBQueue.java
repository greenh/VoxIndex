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
package indexterous.util;

import java.util.LinkedList;

/**
 * Implements a simple-minded thread-safe queue.
 *
 * @param <E> Type of elements being queued.
 */
public class BBQueue<E> {
	
	protected LinkedList<E> queue = new LinkedList<E>();
	protected boolean terminated = false;
	
	
	public BBQueue() { }
	
	
	public void add(E e) {
		synchronized(queue) {
			queue.add(e);
			queue.notify();
		}
	}	// add
	
	public E remove() throws InterruptedException {
		E e;
		synchronized(queue) {
			while (! terminated && queue.size() == 0)
				queue.wait();
			if (queue.size() > 0) {
				e = queue.removeFirst();
			} else
				e = null;
		}
		return e;
	} 	// remove
	
	public void terminate() { 
		synchronized(queue) {
			terminated = true;
			queue.notifyAll();
		}
	}	// terminate
	
	public boolean isTerminated() { return terminated; }
	
	public void reset() { terminated = false; }
	
	@Override public String toString() { 
		return (terminated ? "X" : "") + queue.toString();
	}
	
}
