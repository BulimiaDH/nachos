package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;

import java.util.Iterator;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		list = new LinkedList<SleepingThread>();
		lock = new Lock();
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {

		boolean intStatus = Machine.interrupt().disable();

		long currentTime = Machine.timer().getTime();
		Iterator<SleepingThread> it = list.iterator();

		while(it.hasNext()){  
	        SleepingThread st = it.next();  
			if (st.wakeTime <= currentTime){ 
				st.thread.ready();
				it.remove();
			}
     	}

     	Machine.interrupt().restore(intStatus);
		KThread.yield();
		
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		long wakeTime = Machine.timer().getTime() + x;
		SleepingThread st = new SleepingThread(KThread.currentThread(), wakeTime);
		list.add(st);
		boolean intStatus = Machine.interrupt().disable();
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
		//while (wakeTime > Machine.timer().getTime())
		//	KThread.yield();
	}
	
	private static class SleepingThread {
		KThread thread;
		long wakeTime;
		public SleepingThread(KThread kt, long wt){
			this.thread = kt;
			this.wakeTime = wt;
		}
	}
	LinkedList<SleepingThread> list;
	private Lock lock;
	
}
