package nachos.vm;

import com.sun.tools.internal.xjc.reader.xmlschema.bindinfo.BIConversion;
import nachos.machine.*;
import nachos.userprog.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

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
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
        super.saveState();
        VMKernel.flushTLB(); //with sync
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
    }
    //TODO check will this kind of override work?

    /**
     * check TLB to get ppn
     *
     * @param vpn
     * @return ppn
     */
    private int hitTLB(int vpn) {
        TranslationEntry tlbEntry;
        while (true) {
            for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
                tlbEntry = Machine.processor().readTLBEntry(i);
                if (tlbEntry.vpn == vpn && tlbEntry.valid)
                    return tlbEntry.ppn;
            }
            handleTLBMiss(vpn * pageSize);
            Lib.debug(dbgVM, "dead while");
            //TODO what if not return
        }
    }

    @Override
    protected int pinVirtualPage(int vpn, boolean isUserWrite) {
        if (vpn < 0 || vpn >= pageTable.length) {
            Lib.debug(dbgVM, "VMProcess::pinVirtualPage: fail, vpn is not in the correct range!");
            return -1;
        }
        //TODO lock?
        //check whether it is on the TLB
        int ppn = hitTLB(vpn);
        TranslationEntry entry = pageTable[vpn];
        Lib.assertTrue(entry.vpn == vpn && entry.ppn == ppn && entry.valid, "the page is actual not on phys mem");
        if (isUserWrite) {
            if (entry.readOnly) {
                Lib.debug(dbgRO, "write the readonly Page");
                return -1;
            }
            entry.dirty = true;
        }
        entry.used = true;
        VMKernel.invertedPageTable[ppn].incrementPinCount();
        return ppn;
    }

    @Override
    protected void unpinVirtualPage(int vpn) {
        VMKernel.memoryLock.acquire();
        int ppn = pageTable[vpn].ppn;
        VMKernel.invertedPageTable[ppn].decrementPinCount();
        VMKernel.unpinnedPage.wake();
        VMKernel.memoryLock.release();
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return <tt>true</tt> if successful.
     */
    protected boolean loadSections() {
        //return super.loadSections();
        //lasy loading code here
        // initalize pageTable

        //TODO who will set the read-only flags?
        pageTable = new TranslationEntry[numPages];
        for (int vpn = 0; vpn < numPages; vpn++) {
            pageTable[vpn] = new TranslationEntry(vpn, -1,
                    false, false, false, false);

        }
        //setReadOnlyBit
        for (int s = 0; s< coff.getNumSections();s++){
            CoffSection section = coff.getSection(s);
            for (int i = 0; i<section.getLength();i++){
                int vpn = section.getFirstVPN() + i;
                Lib.debug(dbgRO,"section" + i+ "name:" + section.getName() + "readOnly" + section.isReadOnly());
                pageTable[vpn].readOnly = section.isReadOnly();
                if (pageTable[vpn].readOnly)
                    Lib.debug(dbgRO,"Set readOnly Bit" + vpn + "current Process:"+processID());
            }
        }
        return true;
    }

    /**
     * Precondition: memoryLock is held
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        LinkedList<Integer> spns = new LinkedList<Integer>();
        Lib.debug(dbgVM, "free page size" + UserKernel.freePages.size() + "before unloadSections before the process exit");

        VMKernel.flushTLB();
        //put the physical memory back to free
        for (int vpn = 0; vpn < pageTable.length; vpn++) {
            if (pageTable[vpn].valid) {
                UserKernel.freePages.add(new Integer(pageTable[vpn].ppn));
                pageTable[vpn].valid = false;
            } else {
                if (pageTable[vpn].dirty && pageTable[vpn].ppn != -1)
                    spns.add(pageTable[vpn].ppn);
            }
        }
        VMKernel.swapper.freeAll(spns);
        Lib.debug(dbgVM, "free page size" + UserKernel.freePages.size() + "after unloadSections before the process exit");
    }

    private boolean getPageFromCoff(int vpn, int ppn) {
        CoffSection section;
        for (int i = 0; i < coff.getNumSections(); i++) {
            section = coff.getSection(i);
            if (section.getFirstVPN() <= vpn && vpn < section.getFirstVPN() + section.getLength()) {
                section.loadPage(vpn - section.getFirstVPN(), ppn);
                return true;
            }
        }
        return false;
    }

    /**
     * Precondition: the ppn has been assigned to the faultingPage
     * Load a page from swap file or coff to mem
     *
     * @param faultingPage which page to be loaded
     */
    public boolean loadPageToMem(TranslationEntry faultingPage, int ppn) {
        CoffSection section;
        int vpn = faultingPage.vpn;
        //1 load from swap file
        if (faultingPage.dirty && !faultingPage.readOnly) {
            int spn = faultingPage.ppn;
            VMKernel.swapper.swapIn(spn, ppn);
            Lib.debug(dbgVM, "load from swap file");
        } else if (0 <= vpn && vpn < numPages - stackPages - 1) {
            //2 load from coff
            Lib.assertTrue(getPageFromCoff(vpn, ppn), "load from coff fail");
            Lib.debug(dbgVM, "load from coff");
        }
        //3 stack area, fill 0
        else if (numPages - stackPages - 1 <= vpn && vpn < numPages) {
            Arrays.fill(Machine.processor().getMemory(), ppn * pageSize, (ppn + 1) * pageSize, (byte) 0);
            Lib.debug(dbgVM, "load to stack");
        }
        //4 others: err
        else {
            Lib.debug(dbgVM, "fail, vpn is out of range");
            return false;
        }
        return true;
    }


    /**
     * handle page fault
     *
     * @param faultingPageVPN
     */
    private void handlePageFault(int faultingPageVPN) {
        Lib.debug(dbgVM, "start handle page fault, vpn is " + faultingPageVPN);
        TranslationEntry faultingPage = pageTable[faultingPageVPN];
        //1 Find a place to put the page
        int victimPPN = VMKernel.allocateOnePhysPage(faultingPage);
        Lib.debug(dbgVM, "new ppn = " + victimPPN);
        //2. Put the page to mem
        //now the ppn represent spn if its in the swap file
        Lib.assertTrue(loadPageToMem(faultingPage, victimPPN), "load Page to Mem fail");
        //update page table and inverted page table
        faultingPage.ppn = victimPPN;
        faultingPage.valid = true;
        //unpin the page
        VMKernel.unpinPageFrame(victimPPN);
    }


    private void handleTLBMiss(int vaddr) {
        Lib.debug(dbgVM, "TLBMiss Happens");
        //1. vaddr -> vpn
        int vpn = Processor.pageFromAddress(vaddr);
        int off = Processor.offsetFromAddress(vaddr);

        //TODO:What if pageTable doesn't has this entry? or the vpn is invalid? vpn >= numPages? return -1?
        Lib.assertTrue(vpn >= 0 && vpn < pageTable.length, "The vaddr is invalid");

        //Page fault handler
        if (!pageTable[vpn].valid) {
            Lib.debug(dbgVM, "PageFault Happens");
            handlePageFault(vpn);
        }

        //2. find the TranslationEntry corresponding to the ppn
        TranslationEntry tlbEntry = pageTable[vpn];

        //3. find a spot in the TLB to put the page corresponding to the ppn
        int TLBWritePos = VMKernel.allocateTLBEntry();
        Lib.debug(dbgVM, "New TLB Position" + TLBWritePos);

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

    private static final char dbgRO = 'r';


}

