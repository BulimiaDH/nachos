package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
    /**
     * Allocate a new VM kernel.
     */

    public VMKernel() {
        super();


    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
        super.initialize(args);
        Lib.debug(dbgVM,"VMKernel::initialze: this kernel has " + Machine.processor().getNumPhysPages() + " physical pages");
        invertedPageTable = new PageFrame[Machine.processor().getNumPhysPages()];

        clockHand = 0;
        swapper = new Swapper();
        unpinnedPage = new Condition(memoryLock);
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
        swapper.close();
        Lib.debug(dbgVM, "VMKernel::terminate(): Remaining page on termination: " + freePages.size());
        super.terminate();
    }


    private static void invalidatePTE(TranslationEntry entry, int spn) {
        //no need to sync since TLB will be invalid just after
        entry.valid = false;
        entry.ppn = spn;
    }

    /**
     * Invalidate a tlb entry when given a ppn
     *
     * @param ppn
     */
    private static void invaidateTLBEPPN(int ppn) {
        TranslationEntry tlbEntry;
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            //seems like no need to sync
            tlbEntry = Machine.processor().readTLBEntry(i);
            if (tlbEntry.ppn == ppn) {
                //seems like no need to sync
                tlbEntry.valid = false;
                Machine.processor().writeTLBEntry(i, tlbEntry);
                return;
            }
        }
    }

    /**
     * invalidate all the entries in TLB;
     */
    public static void flushTLB() {
        synchronizeAll();
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            TranslationEntry tlbEntry = Machine.processor().readTLBEntry(i);
            tlbEntry.valid = false;
            Machine.processor().writeTLBEntry(i, tlbEntry);
        }
    }

    /**
     * sync all the TLB PT Entries to have the correct valid/dirty/used bits
     */
    public static void synchronizeAll() {
        for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
            synchronizeEntry(i);
        }
    }

    /**
     * Modify one TLB PT entry to have the correct valid/dirty/used bits
     *
     * @param tlbIndex
     */
    public static void synchronizeEntry(int tlbIndex) {
        TranslationEntry tlbEntry = Machine.processor().readTLBEntry(tlbIndex);
        int vpn = tlbEntry.vpn;
        int ppn = tlbEntry.ppn;
        if (tlbEntry.valid && invertedPageTable[ppn].getVPN() == vpn && invertedPageTable[ppn].isValid()) {
            //Lib.assertTrue(VMKernel.invertedPageTable[ppn].getVPN() ==vpn,"TLB and invertedPageTable sync went wrong, ppn and vpn not matched");
            //Lib.assertTrue(pageTable[vpn].ppn == ppn,"TLB and PageTable sync went wrong, ppn and vpn not matched");
            tlbEntry.dirty = tlbEntry.dirty || invertedPageTable[ppn].isDirty();
            tlbEntry.used = tlbEntry.used || invertedPageTable[ppn].isUsed();
            invertedPageTable[ppn].setDirtyBit(tlbEntry.dirty);
            invertedPageTable[ppn].setUsedBit(tlbEntry.used);
        } else if (tlbEntry.valid && invertedPageTable[ppn].getVPN() == vpn && !invertedPageTable[ppn].isValid()) {
            tlbEntry.valid = false;
            Lib.debug(dbgVM, "failed,valid on TLB but not in mem!");
        }
        Machine.processor().writeTLBEntry(tlbIndex, tlbEntry);
    }

    /**
     * find a spot in the TLB to put the page corresponding to the ppn
     * if not find an invalid place, pick one randomly and overwrite
     *
     * @return the index in the TLB to allocate the new entry
     */
    public static int allocateTLBEntry() {
        int TLBWritePos;
        int TLBSize = Machine.processor().getTLBSize();
        for (int i = 0; i < TLBSize; i++) {
            if (!Machine.processor().readTLBEntry(i).valid) {
                TLBWritePos = i;
                return TLBWritePos;
            }
        }
        //if not find an invalid place, pick one randomly and overwrite

        TLBWritePos = Lib.random(TLBSize);
        //one TLB entry has been evicted, should do TLB-PT synchronization
        synchronizeEntry(TLBWritePos);

        return TLBWritePos;
    }

    /**
     * Precondition: called by handlePageFault
     *
     * @return
     */
    public static int allocateOnePhysPage(TranslationEntry faultingPage) {
        memoryLock.acquire();
        int victimPPN = -1;
        int spn = -1;

        //1.1 There is a free physical page
        if (UserKernel.freePages.size() > 0) {
            Lib.debug(dbgVM, "occupy free page");
            victimPPN = ((Integer) UserKernel.freePages.removeFirst()).intValue();
        }
        //1.2 No free physical page, choose a page to evict: Clock Algorithm
        else {
            victimPPN = clockAlgoToSelectVictim();  //sync happens here, at the beginning of clock
            TranslationEntry victimEntry = invertedPageTable[victimPPN].getEntry();
            if (!invertedPageTable[victimPPN].isReadOnly() && invertedPageTable[victimPPN].isDirty()) {
                spn = VMKernel.swapper.swapOut(victimEntry);
                swapCount++;
                Lib.debug(dbgVM, "-> Paged out #" + swapCount + " \t{ppn=" + victimEntry.ppn + ", spn=" + spn + "}.\t - Proc #" + VMKernel.currentProcess().processID());
            }
            //Invalidate PTE and TLB entry of the victim page
            invalidatePTE(victimEntry, spn);
            invaidateTLBEPPN(victimPPN);
        }
        VMKernel.invertedPageTable[victimPPN] = new PageFrame(currentProcess(), faultingPage);

        Lib.assertTrue(victimPPN != -1);
        memoryLock.release();
        return victimPPN;
    }


    /**
     * Replacement algorithm
     * check the used bit and set, select one to evict
     *
     * @return the assigned ppn
     */
    private static int clockAlgoToSelectVictim() {
        Lib.assertTrue(memoryLock.isHeldByCurrentThread());
        //sync and flush before clock, since we will check the used bit
        flushTLB();
        int totalPinCount = 0;
        while (invertedPageTable[clockHand].isUsed() || invertedPageTable[clockHand].isPinned()) {
            //check pin bit
            if (invertedPageTable[clockHand].isPinned()) {
                totalPinCount += 1;
                // if all pages are pinned
                if (totalPinCount == NUMBER_OF_FRAMES)
                    unpinnedPage.sleep();
            } else {
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
    protected static OpenFile swapFile;
    protected static LinkedList freeSwapPages;
    public static PageFrame[] invertedPageTable;
    public static Swapper swapper;

    private static int clockHand;
    private static int swapCount = 0;
    public static Condition unpinnedPage;
    private static final int NUMBER_OF_FRAMES = Machine.processor().getNumPhysPages();


}

/**
 * Created by onion on 6/6/17.
 */
class PageFrame {
    public PageFrame(UserProcess process, TranslationEntry tEntry) {
        //just try shallow copy here
        this.tEntry = tEntry;
        this.process = process;
        this.pinCount = 0;
        this.pinCountLock = new Lock();

    }

    public TranslationEntry getEntry() {
        return tEntry;
    }

    public void incrementPinCount() {
        pinCountLock.acquire();
        pinCount++;
        pinCountLock.release();
    }

    public void decrementPinCount() {
        pinCountLock.acquire();
        pinCount--;
        pinCountLock.release();
    }

    public boolean isValid() {
        return tEntry.valid;
    }

    public void setDirtyBit(boolean dirtyBit) {
        tEntry.dirty = dirtyBit;
    }

    public void setUsedBit(boolean usedBit) {

        tEntry.used = usedBit;
    }

    public boolean isReadOnly() {
        return tEntry.readOnly;
    }

    public boolean isDirty() {
        return tEntry.dirty;
    }

    public UserProcess getProcess() {
        return process;
    }

    public int getVPN() {
        return tEntry.vpn;
    }

    public boolean isUsed() {
        return tEntry.used;
    }

    public boolean isPinned() {
        return pinCount > 0;
    }

    public boolean setUnused() {
        return tEntry.used = false;
    }

    private UserProcess process;
    private TranslationEntry tEntry;
    private int pinCount;
    private Lock pinCountLock;
}


