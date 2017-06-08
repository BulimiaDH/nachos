package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}


	/**
	 * invalidate all the entries in TLB;
	 */
	private void flushTLB(){
		for (int i = 0; i < Machine.processor().getTLBSize(); i++){
			TranslationEntry tlbEntry = Machine.processor().readTLBEntry(i);
			tlbEntry.valid = false;
			Machine.processor().writeTLBEntry(i, tlbEntry);
		}
	}
	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
		synchronizeTLBPT();
		flushTLB();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
	}
//	protected int pinVirtualPage(int vpn, boolean isUserWrite) {
//		//TODO
//		return super.pinVirtualPage(vpn, isUserWrite);
//	}
//	protected void unpinVirtualPage(int vpn) {
//		//TODO
//		//unpinnedPage.wakeup();
//	}
	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		return super.loadSections();
//		//TODO:lasy loading code heres
//		// initalize pageTable
//		pageTable = new TranslationEntry[numPages];
//		for (int vpn=0; vpn<numPages; vpn++) {
//			pageTable[vpn] = new TranslationEntry(vpn, -1,
//					false, false, false, false);
//		}
//		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
//		for (int vpn=0; vpn<pageTable.length; vpn++) {
//			if (pageTable[vpn].valid) {
//				UserKernel.freePages.add(new Integer(pageTable[vpn].ppn));
//				pageTable[vpn].valid = false;
//			}
//			//TODO question: do we need to synchronize TLB and inverted page Table?
//		}
		super.unloadSections();
	}

	/**
	 * sync all the TLB PT Entries to have the correct valid/dirty/used bits
	 */
	private void synchronizeTLBPT(){
		for (int i = 0; i< Machine.processor().getTLBSize(); i++){
			synchronizeTLBPTEntry(i);
		}
	}

	/**
	 * Modify one TLB PT entry to have the correct valid/dirty/used bits
	 * @param tlbIndex
	 */
	public void synchronizeTLBPTEntry(int tlbIndex){
		TranslationEntry tlbEntry = Machine.processor().readTLBEntry(tlbIndex);
		int vpn = tlbEntry.vpn;

		if (tlbEntry.valid && pageTable[vpn].valid) {
			pageTable[vpn].dirty = tlbEntry.dirty || pageTable[vpn].dirty;
			pageTable[vpn].used = tlbEntry.used || pageTable[vpn].used;
			tlbEntry.dirty = tlbEntry.dirty || pageTable[vpn].dirty;
			tlbEntry.used = tlbEntry.used || pageTable[vpn].used;
		}
		else if (tlbEntry.valid && !pageTable[vpn].valid){
			tlbEntry.valid = false;
			Lib.debug(dbgVM,"valid on TLB but not on page table!");
		}
		Machine.processor().writeTLBEntry(tlbIndex, tlbEntry);
	}


//    //TODO: when evict a physical page
//	//TODO: ?synchronize all TLB or just one entry?


//	private int clockAlgoToSelectVictim(){
	//TODO: sync before use it, before delete it
	//TODO: sync TLB All and PageTable, inverted PageTable, since we will check the used bit
//		int toEvictPPN = -1;
//		int victim = 0;
//		//check the used bit and set, select one to evict
//		//Replacement algorithm
//		//TODO check pin bit
//		while(VMKernel.invertedPageTable[victim].tEntry.used){
//			invertedPageTable[victim].tEntry.used = false;
//
//			victim = (victim + 1) % NUMBER_OF_FRAMES;
//		}
//		toEvict = victim;
//		victim = (victim + 1) % NUMBER_OF_FRAMES;
//
//		if (all pages are pinned){
//			unpinnedPage.sleep();
//		}
//		return toEvictPPN;
	    //TODO: set the old entry on page table and TLB using that ppn to invalid
//
//	}
//	private void handlePageFault(int vpn){
//		//TODO: Ask the kernel for a free physical page
//		//TODO: 1There is a free physical page
//		int ppn;
//		//Allocate one from list; //same with proj2
//		if (UserKernel.freePages.size() > 0)
//		{
//			ppn = ((Integer)UserKernel.freePages.removeFirst()).intValue();
//		}
//		//TODO: 2No free physical page, choose a page to evict: Clock Algorithm
//		else{
//			//No free memory, need to evict a page
//			Sync TLB entries;
//			Select a victim for replacement; //Clock algorithm
//			if the select one is not read-only and is dirty, write to SWAP FILE //
//			victim = clockAlgoToSelectVictim();
//			//TODO sync TLB
//			if (victim is not read-only and dirty){
//				swap out;
//				//File.read(index*pageSize, memory,paddr, pageSize);
//			}
//			Invalidate PTE and TLB entry of the victim page
//		}
//		//Allocate a physical page
//		if (pageTable[vpn].dirty){
//			//TODO:swap in
//			spn = 	pageTable[vpn].ppn;
//			// File.write(index*pageSize, memory,paddr, pageSize);
//		}
//		else{
//			if (coff area){
//				load page from coff section
//
//				coff.getNumSections()
//				coff.getSection()
//				section.getFirstVPN()
//				section.loadPage(section_num,ppn)
//			}
//		}
//		update page table and inverted page table
//		pageTable[vpn].valid = true;
//
//	}

	/**
	 * find a spot in the TLB to put the page corresponding to the ppn
	 * if not find an invalid place, pick one randomly and overwrite
	 * @return the index in the TLB to allocate the new entry
	 */
	private int allocateTLBEntry(){
		int TLBWritePos;
		int TLBSize = Machine.processor().getTLBSize();
		for (int i = 0; i < TLBSize; i++){
			if (!Machine.processor().readTLBEntry(i).valid){
				TLBWritePos = i;
				return TLBWritePos;
			}
		}
		//if not find an invalid place, pick one randomly and overwrite
		TLBWritePos = Lib.random(TLBSize);
		//one TLB entry has been evicted, should do TLB-PT synchronization
		synchronizeTLBPTEntry(TLBWritePos);

		return TLBWritePos;
	}

	private void handleTLBMiss(int vaddr){
		Lib.debug(dbgVM,"TLBMiss Happens");
		//1. vaddr -> vpn
		int vpn = Processor.pageFromAddress(vaddr);
		int off = Processor.offsetFromAddress(vaddr);

		//TODO: What if pageTable doesn't has this entry? or the vpn is invalid? vpn >= numPages? return -1?
		Lib.assertTrue(vpn >= 0 && vpn < pageTable.length, "The vaddr is invalid");

		//Page fault handler
		if (!pageTable[vpn].valid){
			Lib.debug(dbgVM,"PageFault Happens");
			//TODO: handlePageFault
			//handlePageFault(vpn);
		}

		//2. find the TranslationEntry corresponding to the ppn
		TranslationEntry tlbEntry = pageTable[vpn];

		//3. find a spot in the TLB to put the page corresponding to the ppn
		int TLBWritePos = allocateTLBEntry();
		Lib.debug(dbgVM,"New TLB Position" + TLBWritePos);

		//4. put the TranslationEntry corresponding to the ppn in the TLB
		Machine.processor().writeTLBEntry(TLBWritePos, tlbEntry);
	}


	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case Processor.exceptionTLBMiss:
				handleTLBMiss(processor.readRegister(Processor.regBadVAddr));
				break;
		default:
			super.handleException(cause);
			break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
