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
		int word;
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
		/*
		while(somebodyIsSpeaking){
			block myself
		} 
		someoneIsSpeaking = true;
		this.word  = word;
		wake up a listener if they are waiting for someone to speak
		wait until a listener has acknowledge they have heard my word
		*/

	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 * 
	 * @return the integer transferred.
	 */
	public int listen() {
		
		// if (!someoneIsSpeaking){
		// 	wake up someone who is waiting to speak
		// 	wait until someone is someone
		// }
		// int heardWord = this.word;
		// someoneIsSpeaking = false;
		// acknowledge to the speaker that you've heard them
		// return the heardwrod;
		return 0;

	}
	boolean someoneIsSpeaking = false;
}
