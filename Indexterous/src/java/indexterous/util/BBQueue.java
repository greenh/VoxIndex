package indexterous.util;

import java.util.LinkedList;

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
