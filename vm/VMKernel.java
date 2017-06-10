package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.Arrays;
import java.util.LinkedList;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {super();}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		invertedPageTable = new PageFrame[Machine.processor().getNumPhysPages()];
		clockHand = 0;
		swapper = new Swapper();
		unpinnedPage = new Condition(physPageLock);
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


	private static void invalidatePTE(TranslationEntry entry, int spn){
		//no need to sync since TLB will be invalid just after
		entry.valid = false;
		entry.ppn = spn;
	}

	/**
	 * Invalidate a tlb entry when given a ppn
	 * @param ppn
	 */
	private static void invaidateTLBEPPN(int ppn){
		TranslationEntry tlbEntry;
		for (int i = 0; i< Machine.processor().getTLBSize(); i++){
			//seems like no need to sync
			tlbEntry = Machine.processor().readTLBEntry(i);
			if (tlbEntry.ppn == ppn){
				//seems like no need to sync
				tlbEntry.valid = false;
				Machine.processor().writeTLBEntry(i, tlbEntry);
				return;
			}
		}
	}

	/**
	 * Precondition: called by handlePageFault
	 *
	 * @return
	 */
	public static int occupyOnePhysPage(TranslationEntry faultingPage){
		physPageLock.acquire();
		int victimPPN = -1;
		int spn = -1;

		//1.1 There is a free physical page
		if (UserKernel.freePages.size() > 0)
		{
			Lib.debug(dbgVM,"occupy free page");
			victimPPN = ((Integer)UserKernel.freePages.removeFirst()).intValue();
		}
		//1.2 No free physical page, choose a page to evict: Clock Algorithm
		else{
			victimPPN = clockAlgoToSelectVictim();  //sync happens here, at the beginning of clock
			TranslationEntry victimEntry = invertedPageTable[victimPPN].getEntry();
			if (!invertedPageTable[victimPPN].isReadOnly() && invertedPageTable[victimPPN].isDirty()){
				//TODO: swap out;
				spn = VMKernel.swapper.swapOut(victimEntry);
				swapCount ++;
				Lib.debug(dbgVM, "-> Paged out #" + swapCount + " \t{ppn=" + victimEntry.ppn + ", spn=" + spn + "}.\t - Proc #" + VMKernel.currentProcess().processID());
			}
			//Invalidate PTE and TLB entry of the victim page
			invalidatePTE(victimEntry, spn);
			invaidateTLBEPPN(victimPPN);
		}
		VMKernel.invertedPageTable[victimPPN] = new PageFrame(currentProcess(), faultingPage);

		Lib.assertTrue(victimPPN != -1);
		physPageLock.release();
		return victimPPN;
	}

	/**
	 * synchronize the entry on invertedPageTable according to TLB and ppn
	 */
	private static void synchronizefromTLB(){
		for (int i = 0; i< Machine.processor().getTLBSize(); i++){
			TranslationEntry tEntry = Machine.processor().readTLBEntry(i);
			if (tEntry.valid) {
				int ppn = tEntry.ppn;
				tEntry.used = tEntry.used || invertedPageTable[ppn].isUsed();
				tEntry.dirty = tEntry.dirty || invertedPageTable[ppn].isDirty();
				invertedPageTable[ppn].

			}

		}

	}
	/**
	 *  Replacement algorithm
	 *  check the used bit and set, select one to evict
	 * @return the assigned ppn
	 */
	private static int clockAlgoToSelectVictim(){
		Lib.assertTrue(physPageLock.isHeldByCurrentThread());
		//TODO: sync before use it, before delete it
		flushTLB();
		// sync TLB All and PageTable, inverted PageTable, since we will check the used bit
		synchronizefromTLB();
		int totalPinCount = 0;
		while(invertedPageTable[clockHand].isUsed() || invertedPageTable[clockHand].isPinned()){
			//check pin bit
			if (invertedPageTable[clockHand].isPinned()) {
				totalPinCount += 1;
				// if all pages are pinned
				if (totalPinCount == NUMBER_OF_FRAMES)
					unpinnedPage.sleep();
			}
			else {
				totalPinCount = 0;
				invertedPageTable[clockHand].setUnused();
			}
			clockHand = (clockHand + 1) % NUMBER_OF_FRAMES;
		}
		int toEvictPPN = clockHand;
		clockHand = (clockHand + 1) % NUMBER_OF_FRAMES;
		return toEvictPPN;
	}


	public int getNumPhysPages() {
		return Machine.processor().getNumPhysPages();
	}
	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';
	//TODO
	//OpenFile swapFile = fileSystem.open("swapFile",true);
	private static LinkedList freeSwapPages;
	public static PageFrame[] invertedPageTable;
	public static Swapper swapper;
	protected static Lock physPageLock;

	private static int clockHand;
	private static int swapCount = 0;
	public static Condition unpinnedPage;
	private static final int NUMBER_OF_FRAMES = Machine.processor().getNumPhysPages();


}

