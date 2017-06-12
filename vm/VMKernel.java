package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
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
     * Precondition: physPageLock is acquired.
     * Atomic: make sure the PTE is valid and pin it
     * @param ppn
     */
    //TODO check who call this and lock
    public static void pinPageFrame(int ppn) {
//        Lib.assertTrue(memoryLock.isHeldByCurrentThread());
        Lib.assertTrue(invertedPageTable[ppn].getProcess().processID() == VMKernel.currentProcess().processID(),
                "A non-owner process is pinning a page!");
        invertedPageTable[ppn].incrementPinCount();
        Lib.debug(dbgVM,"- pinning physical page #" + ppn + ". pinCount is " + invertedPageTable[ppn].getPinCount() + " now \t - proc #" + VMKernel.currentProcess().processID());
    }
    private static int unpinCount = 1;
    // TODO assert that only the owner process can unpin the page

    public static void unpinPageFrame(int ppn) {
        memoryLock.acquire();
        Lib.debug(dbgVM,"- unpinning physical page #" + ppn + ". pinCount was " + invertedPageTable[ppn].getPinCount() + "\t - proc #" + VMKernel.currentProcess().processID());
        unpinCount++;
        Lib.assertTrue(0<=ppn && ppn < VMKernel.getNumPhysPages());
        Lib.assertTrue(invertedPageTable[ppn].getProcess().processID() == VMKernel.currentProcess().processID(),
                "A non-owner process is unpinning a page!");
        invertedPageTable[ppn].decrementPinCount();
        unpinnedPage.wake();
        memoryLock.release();
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
     * Precondition: called by handlePageFault, memoryLock is acquired
     *
     * @return
     */
    public static int allocateOnePhysPage(TranslationEntry faultingPage) {
        memoryLock.acquire();
        int victimPPN;
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
        //pin the page here, unpin after loaded
        pinPageFrame(victimPPN);
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
                if (totalPinCount == NUMBER_OF_FRAMES) {
                    unpinnedPage.sleep();
                    totalPinCount = 0;
                }
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




    public static int getNumPhysPages() {
        return Machine.processor().getNumPhysPages();
    }

    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;

    private static final char dbgVM = 'v';
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
    public int getPinCount(){
        return pinCount;
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


/**
 * SwapFile :
 * A file on disk that simulates more memory than a computer may actually have.
 * It does this by writing pages onto a file in main memory and swapping it in
 * with main memory and vise versa.
 */

class Swapper {

    /**
     *         - Free all swapped pages on process exit
     *         - Doubling the size of the linked list if we run out of space DONE
     *         - Initializing swap file and free swap pages DONE
     *         - Swapping in files from the swap file to main memory DONE
     *         - Swapping out files from main memory to disk (swp file) DONE
     *         - Allocating a swap page to a page to be swapped out DONE
     *         - Deallocate a swap page to a page to be swapped in DONE
     */

    public Swapper() {
        swapperLock = new Lock();
        swapFile = VMKernel.fileSystem.open(swapFileName ,true);
        freeSwapPages = new LinkedList<Integer>();

        for (int spn=0; spn<Machine.processor().getNumPhysPages(); spn++)
            freeSwapPages.add(new Integer(spn));

        numSwapPages = freeSwapPages.size();
    }




    /**
     * Test this swapper.
     */
    public void selfTest() {
        Swapper swap = new Swapper();

    }

    /**
     * write from swapfile(spn) to physical mem(ppn)
     * Swap in data from files from swapfile to main memory
     * @param spn Swap Page Number - The Page Number in the swap file;
     * @param ppn Physical Page Number - the Destination page number in physical memory
     */
    public void swapIn(int spn, int ppn){

        //check if file is dirty. Only dirty files are swapped return -1
//        if (!entry.dirty) {
//            Lib.debug(dbgVM, "Entry is not dirty, do not need to swap");
//            return -1;
//        }

        // TODO check if file is pinned. Pinned files cannot be swapped
        /**
         * If Pinned -> Lib.debug(dbgVM, "Entry is pinned");
         */

        swapperLock.acquire();

        byte[] pageData = new byte[pageSize];

        // Read from swap file
        int actualSize = swapFile.read(spn*pageSize, pageData, 0, pageSize);
        Lib.assertTrue( actualSize == pageSize);

        // Write to main memory
        byte[] memory = Machine.processor().getMemory();
        System.arraycopy(pageData, 0, memory, ppn*pageSize, pageSize);

        swapperLock.release();
    }

    /**
     * swap the written page to SwapFile
     * Swap out data from main memory to the swap file.
     * @param victimEntry the translation entry which will be swapped out
     * @return spn
     */
    public int swapOut(TranslationEntry victimEntry) {

        swapperLock.acquire();
        //check if file is dirty. Only dirty files are swapped return -1
        if (!victimEntry.dirty) {
            Lib.debug(dbgVM, "Entry is not dirty, do not need to swap");
            swapperLock.release();
            return -1;
        }

        // To be valid in this case, would mean that the entry is in memory
        // It should be valid because we are moving from main memory to swap file
        if (!victimEntry.valid) {
            Lib.debug(dbgVM, "Entry is not in main memory");
            swapperLock.release();
            return -1;
        }

        //TODO check if file is pinned. Pinned files cannot be swapped
        /**
         * If Pinned -> Lib.debug(dbgVM, "Entry is pinned");
         */

        if (freeSwapPages.size() == 0) { // If size returns 0, allocate more space
            increaseSize();
        }

        int spn = allocateSwapPage();

        // Read from physical frame in memory
        byte[] memory = Machine.processor().getMemory();
        byte[] pageData = new byte[pageSize];
        System.arraycopy(memory, pageSize * victimEntry.ppn, pageData, 0, pageSize);

        // Write to swapfile in disk
        Lib.assertTrue(swapFile.write(spn*pageSize, pageData, 0, pageSize) == pageSize);

        swapperLock.release();
        return spn;
    }

    public boolean increaseSize() {
        //TODO keep track of number of swap pages
        int swpSize = numSwapPages;
        for (int i = 0; i < swpSize; i++) {
            freeSwapPages.add(new Integer(swpSize + i));
            numSwapPages++;
        }
        return true;
    }

    private Integer allocateSwapPage() {
        if (freeSwapPages.size() != 0) {
            return freeSwapPages.remove();
        } else {
            increaseSize();
            return freeSwapPages.remove();
        }
    }

    private boolean deallocateSwapPage(int spn) {
        freeSwapPages.add(new Integer(spn));
        return true;
    }

    public void freeAll(LinkedList<Integer> spns) {
        swapperLock.acquire();

        while (spns.size() > 0) {
            deallocateSwapPage(spns.poll());
        }

        swapperLock.release();
    }

    public void close() {
        swapFile.close();
        VMKernel.fileSystem.remove(swapFileName);
    }

    private static final int pageSize = Processor.pageSize;
    private static final String swapFileName = "swapFile.swp";
    private Lock swapperLock;
    private int numSwapPages;

    protected static OpenFile swapFile;
    protected static LinkedList<Integer> freeSwapPages;

    private static final char dbgVM = 'v';
}


