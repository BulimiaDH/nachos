package nachos.vm;

/**
 * Created by onion on 6/8/17.
 */

/**
 * SwapFile :
 * A file on disk that simulates more memory than a computer may actually have.
 * It does this by writing pages onto a file in main memory and swapping it in
 * with main memory and vise versa.
 */

public class Swapper {

    /**
     * TODO// :- Free all swapped pages on process exit
     *         - Doubling the size of the linked list if we run out of space DONE
     *         - Initializing swap file and free swap pages DONE
     *         - Swapping in files from the swap file to main memory DONE
     *         - Swapping out files from main memory to disk (swp file) DONE
     *         - Allocating a swap page to a page to be swapped out DONE
     *         - Deallocate a swap page to a page to be swapped in DONE
     */

    public Swapper() {

    }

    /**
     * Test this swapper.
     */
    public void selfTest() {
        Swapper();
        LinkedList test;s
    }

    /**
     * write from swapfile(spn) to physical mem(ppn)
     * Swap in data from files from swapfile to main memory
     * @param spn Swap Page Number - The Page Number in the swap file;
     * @param ppn Physical Page Number - the Destination page number in physical memory
     */
    public void swapIn(int spn, int ppn){

        //check if file is dirty. Only dirty files are swapped return -1
        if (!entry.dirty) {
            Lib.debug(dbgVM, "Entry is not dirty, do not need to swap");
            return -1;
        }

        // TODO check if file is pinned. Pinned files cannot be swapped
        /**
         * If Pinned -> Lib.debug(dbgVM, "Entry is pinned");
         */

        byte[] pageData = [pageSize];

        // Read from swap file
        swapFile.read(spn*pageSize, pageData, 0, pageSize);

        // Write to main memory
        byte[] memory = Machine.processor().getMemory();
        System.arraycopy(pageData, 0, memory, ppn*pageSize, pageSize);
    }

    /**
     * swap the written page to SwapFile
     * Swap out data from main memory to the swap file.
     * @param victimEntry the translation entry which will be swapped out
     * @return spn
     */
    public int swapOut(TranslationEntry victimEntry) {

        //check if file is dirty. Only dirty files are swapped return -1
        if (!entry.dirty) {
            Lib.debug(dbgVM, "Entry is not dirty, do not need to swap");
            return -1;
        }

        // To be valid in this case, would mean that the entry is in memory
        // It should be valid because we are moving from main memory to swap file
        if (!entry.valid) {
            Lib.debug(dbgVM, "Entry is not in main memory");
            return -1;
        }

        // TODO check if file is pinned. Pinned files cannot be swapped
        /**
         * If Pinned -> Lib.debug(dbgVM, "Entry is pinned");
         */

        if (!freeSwapPages.size()) { // If size returns 0, allocate more space
            increaseSize();
        }

        spn = allocateSwapPage();
        // Read from physical frame in memory
        byte[] memory = Machine.processor().getMemory();
        ArrayFile mem = ArrayFile(memory);

        byte[] pageData = [pageSize];
        mem.read(victimEntry.ppn * pageSize, pageData, 0, pageSize);

        // Write to swapfile in disk
        swapFile.write(pageData, 0, pageSize);

        //File.read(index*pageSize, memory,paddr, pageSize);
        return spn;
    }

    public boolean increaseSize() {
        int swpSize = freeSwapPages.size();
        for (int i = 1; i <= swpSize; i++) {
            freeSwapPages.add(new Integer(swpSize + i));
        }
        return true;
    }

    private int allocateSwapPage() {
        return freeSwapPages.remove();
    }

    private boolean deallocateSwapPage(spn) {
        freeSwapPages.add(new Integer(spn));
        return true;
    }

    public boolean freeAll(LinkedList spns) {
        while (spns.size() > 0) {
            deallocateSwapPage(spns.poll());
        }
        return true;
    }

    private static final int pageSize = Processor.pageSize;

}
