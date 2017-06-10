package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.LinkedList;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
		invertedPageTable = new PageFrame[Machine.processor().getNumPhysPages()];
		swapFile = fileSystem.open("swapFile" ,true);

		for (int spn=0; spn<Machine.processor().getNumPhysPages(); spn++)
			freeSwapPages.add(new Integer(spn));
		}
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		//TODO
		//swapFile.close();
		//fileSystem.remove("swapFile");
		super.terminate();
	}



	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';
	//TODO
	protected static OpenFile swapFile;
	protected static LinkedList freeSwapPages;
	public static PageFrame[] invertedPageTable;
	public static Swapper swapper;
}

