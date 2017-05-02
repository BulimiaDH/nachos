package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		this.cLock = new Lock();
		this.Speaker = new Condition(this.cLock);
		this.Listener = new Condition(this.cLock);
		this.Ack = new Condition(this.cLock);

	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 * 
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 * 
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		this.cLock.acquire();
		while(this.someoneIsSpeaking)
			this.Speaker.sleep();
		this.someoneIsSpeaking = true;
		this.word  = word;
		this.Listener.wake();
        this.Ack.sleep();
        this.cLock.release();
		
	}
	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		this.cLock.acquire();
		while (!this.someoneIsSpeaking)
			this.Listener.sleep();
		int word = this.word;
		this.someoneIsSpeaking = false;
		this.Ack.wake();
		this.Speaker.wake();
		this.cLock.release();
		return word;
	}
	boolean someoneIsSpeaking = false;
	int word;
	Lock cLock;
	Condition Speaker, Listener, Ack;
}
