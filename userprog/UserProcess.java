package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.VMProcess;

import java.nio.ByteBuffer;
import java.util.*;

import java.io.EOFException;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		pid = numProcess++;

		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

		fdFileTable = new HashMap<Integer, OpenFile>();
		nameFdTable = new HashMap<String, Integer>();

		fdFileTable.put(0, UserKernel.console.openForReading());
		fdFileTable.put(1, UserKernel.console.openForWriting());
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		//return (UserProcess) Lib.constructObject(Machine.getProcessClassName());

		String name = Machine.getProcessClassName();
		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader.  Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals("nachos.userprog.UserProcess")) {
			return new UserProcess();
		} else if (name.equals("nachos.vm.VMProcess")) {
			return new VMProcess();
		} else {
			return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		uThread = new UThread(this);
		uThread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		if (data == null) return 0;

		byte[] memory = Machine.processor().getMemory();

		int vpn = Processor.pageFromAddress(vaddr);
		int vpnOffset = Processor.offsetFromAddress(vaddr);

        TranslationEntry entry = null;

		try {
			entry = getTranslationEntry(vpn);
			entry.used = true;
		}
		catch(ArrayIndexOutOfBoundsException e) {
		    System.out.println("IndexOutOfBoundsException: " + e.getMessage());
		    return 0;
        }

        // Initial address in memory
		int address = entry.ppn * pageSize + vpnOffset;

		if (entry == null)
			return 0;

		int amount = 0;
		int bufOffset = offset; // offset of buffer if we need to start somewhere in mid
		int pageOffset = vpnOffset;
		int bytesToWrite = length;

		while (amount < length) {
			// The data we wish to write will extend off the size of the page
			if (pageOffset + bytesToWrite > pageSize) {

				// Need to save the context of all data to account for the next page on the table
				int diff = pageSize - pageOffset;

				// Read all the way to the end of the page table
				System.arraycopy(memory, address, data, offset, diff);

				// Update the data for the next page
				amount = amount + diff;
				bufOffset = bufOffset + diff; // Where to start in the buffer
				bytesToWrite = length - amount;
				vpn = vpn + 1; // GO to next page table

				if (vpn >= pageTable.length) break;

				else {

					// Set to un-used
					pageTable[vpn - 1].used = false;
					entry = pageTable[vpn];
					if (!entry.valid)  break;

					entry.used = true;
					pageOffset = 0;
					address = entry.ppn * pageSize;
				}
			}
			else {
				// Base case - just copy array and increase amount to length
				System.arraycopy(memory, address, data, bufOffset, bytesToWrite);
				amount = amount + bytesToWrite;
				bufOffset = bufOffset + bytesToWrite;
				pageTable[vpn].used = false;
			}

		}

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 *
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		int vpn = Processor.pageFromAddress(vaddr);
		int vpnOffset = Processor.offsetFromAddress(vaddr);

		TranslationEntry entry = pageTable[vpn];

		if (entry == null) return 0;

		if (entry.readOnly) return 0;

		entry.used = true;
		int address = entry.ppn*pageSize + vpnOffset;

		int amount = 0;
		int bufOffset = offset; // offset of buffer if we need to start somewhere in mid
		int pageOffset = vpnOffset;
		int bytesToWrite = length;

		// Same as readvirtual memory, just in other direction
		while (amount < length) {
			if (pageOffset + bytesToWrite > pageSize) {
				int diff = pageSize - pageOffset;
				System.arraycopy(data, bufOffset, memory, address, diff);

				// Update the new values for the next page
				amount = amount + diff;
				bufOffset = bufOffset + diff;
				bytesToWrite = length - amount;
				vpn = vpn + 1;

				// Check if the vpn > pageTable.length
				if (vpn >= pageTable.length) break;
				else {
					// Set to un-used
					pageTable[vpn - 1].used = false;
					entry = pageTable[vpn];

					// If entry is not valid, break
					if (!entry.valid)  break;
					entry.used = true;
					pageOffset = 0; // Move on to next page
					address = entry.ppn * pageSize;

				}

			}

			else {
				// Base case - just copy array and increase amount to length
				System.arraycopy(data, bufOffset, memory, address, bytesToWrite);
				amount = amount + bytesToWrite;
				bufOffset = bufOffset + bytesToWrite;
				pageTable[vpn].used = false;
			}

		}



		return amount;
	}

	public TranslationEntry getTranslationEntry(int vpn) {
        System.out.println("vpn:" +pageTable[vpn].vpn + "--> ppn:" + pageTable[vpn].ppn);
        return pageTable[vpn];
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false); //true create false just open
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		}
		catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 *
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		pageTable = ((UserKernel) Kernel.kernel).allocatePages(numPages);
		for (int i = 0; i < pageTable.length; i++) {
			pageTable[i].vpn = i;
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");


			if (section.isReadOnly()) {
				pageTable[s].readOnly = true;
			}

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// loadPage into physical address given by pageTable
				section.loadPage(i, pageTable[vpn].ppn);


			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		((UserKernel) Kernel.kernel).deallocatePages(pageTable);
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	/** Halt the Nachos machine by calling Machine.halt(). Only the root process
     * (the first process, executed by UserKernel.run()) should be allowed to
     * execute this syscall. Any other process should ignore the syscall and return
     * immediately.
     */
	private int handleHalt() {
		// the first process in the system. If another process attempts to invoke halt, the system call should be ignored and return immediately.
		if (pid == 1)
			Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

    /**
     * Attempt to open the named file and return a file descriptor.
     *
     * Note that open() can only be used to open files on disk; open() will never
     * return a file descriptor referring to a stream.
     *
     * Returns the new file descriptor, or -1 if an error occurred.
     */
	private int handleOpen(int nameVA){
		int fd; // file descriptor
		for (fd = 2; fd < maxNumOpenFile; fd ++) {
			if (!fdFileTable.containsKey(fd)) {
				String fileName = readVirtualMemoryString(nameVA, maxFileNameLength);
				if (fileName == null || fileName.length() > 256){
					return -1;
				}
				OpenFile file = ThreadedKernel.fileSystem.open(fileName, false);
				if (file == null){
					return -1;
				}
				fdFileTable.put(fd, file);
				nameFdTable.put(fileName, fd);
				System.out.println(fileName + "is created/opened successfully. The fd is " + fd);
				return fd;
			}
		}
		return -1;
	}

    /**
     * Attempt to open the named disk file, creating it if it does not exist,
     * and return a file descriptor that can be used to access the file.
     *
     * Note that creat() can only be used to create files on disk; creat() will
     * never return a file descriptor referring to a stream.
     *
     * Returns the new file descriptor, or -1 if an error occurred.
     */
	private int handleCreate(int nameVA){
		int fd;
		for (fd = 2; fd < maxNumOpenFile; fd ++) {
			if (!fdFileTable.containsKey(fd)) {
				String fileName = readVirtualMemoryString(nameVA, maxFileNameLength);
				if (fileName == null || fileName.length() > 256){
					return -1;
				}
				OpenFile file = ThreadedKernel.fileSystem.open(fileName, true);
				if (file == null){
					return -1;
				}
				fdFileTable.put(fd, file);
				nameFdTable.put(fileName, fd);
				System.out.println(fileName + "is created/opened successfully. The fd is " + fd);
				return fd;
			}
		}
		return -1;
	}


    /**
     * Attempt to read up to count bytes into buffer from the file or stream
     * referred to by fileDescriptor.
     *
     * On success, the number of bytes read is returned. If the file descriptor
     * refers to a file on disk, the file position is advanced by this number.
     *
     * It is not necessarily an error if this number is smaller than the number of
     * bytes requested. If the file descriptor refers to a file on disk, this
     * indicates that the end of the file has been reached. If the file descriptor
     * refers to a stream, this indicates that the fewer bytes are actually
     * available right now than were requested, but more bytes may become available
     * in the future. Note that read() never waits for a stream to have more data;
     * it always returns as much as possible immediately.
     *
     * On error, -1 is returned, and the new file position is undefined. This can
     * happen if fileDescriptor is invalid, if part of the buffer is read-only or
     * invalid, or if a network stream has been terminated by the remote host and
     * no more data is available.
     */
	private int handleRead(int fd, int memVA, int count) {
		byte[] localBuf = new byte[localBufferSize];
		if (!fdFileTable.containsKey(fd))
			return -1;
		OpenFile file = fdFileTable.get(fd);
		int actualCount;
		int totalRead = 0;
		int writePos = memVA;

		do {
			actualCount = file.read(localBuf, 0, localBufferSize);
			if (actualCount == -1){
				return -1;
			}
			if (actualCount <= count) {
				count -= actualCount;
			} else {
				actualCount = count;
				count = 0;
			}
			writeVirtualMemory(writePos, localBuf, 0, actualCount);
			totalRead += actualCount;
			writePos += actualCount;
		}
		while (actualCount == localBufferSize);
		return totalRead;
	}
		//Use fileTable[fd].read() to read from file to a local buffer of limited size
		///Then write it into the user inputted buffer



    /**
     * Attempt to write up to count bytes from buffer to the file or stream
     * referred to by fileDescriptor. write() can return before the bytes are
     * actually flushed to the file or stream. A write to a stream can block,
     * however, if kernel queues are temporarily full.
     *
     * On success, the number of bytes written is returned (zero indicates nothing
     * was written), and the file position is advanced by this number. It IS an
     * error if this number is smaller than the number of bytes requested. For
     * disk files, this indicates that the disk is full. For streams, this
     * indicates the stream was terminated by the remote host before all the data
     * was transferred.
     *
     * On error, -1 is returned, and the new file position is undefined. This can
     * happen if fileDescriptor is invalid, if part of the buffer is invalid, or
     * if a network stream has already been terminated by the remote host.
     */
	private int handleWrite(int fd, int memVA, int count){
		byte[] localBuf = new byte[localBufferSize];
		if (!fdFileTable.containsKey(fd))
			return -1;
		if (count < 0)
		    return -1;
		if (count == 0)
		    return 0;
		OpenFile file = fdFileTable.get(fd);
		int actualCount;
		int totalWrite = 0;
		int readPos = memVA;
		int leftToWrite = count;
		do {
			actualCount = readVirtualMemory(readPos, localBuf, 0, localBufferSize);
			if (actualCount <= leftToWrite) {
				leftToWrite -= actualCount;
			} else {
				actualCount = leftToWrite;
				leftToWrite = 0;
			}

			if (file.write(localBuf, 0, actualCount) == -1)
				return -1;
			totalWrite += actualCount;
			readPos += actualCount;
		}
		while (actualCount == localBufferSize);
		if (totalWrite == count) {
			return totalWrite;
		}
		else
			return -1;
	}

    /**
     * Close a file descriptor, so that it no longer refers to any file or stream
     * and may be reused.
     *
     * If the file descriptor refers to a file, all data written to it by write()
     * will be flushed to disk before close() returns.
     * If the file descriptor refers to a stream, all data written to it by write()
     * will eventually be flushed (unless the stream is terminated remotely), but
     * not necessarily before close() returns.
     *
     * The resources associated with the file descriptor are released. If the
     * descriptor is the last reference to a disk file which has been removed using
     * unlink, the file is deleted (this detail is handled by the file system
     * implementation).
     *
     * Returns 0 on success, or -1 if an error occurred.
     */
	 private int handleClose(int fd){
	     if (!fdFileTable.containsKey(fd))
	         return -1;
        OpenFile file = fdFileTable.get(fd);
        file.close();

        fdFileTable.remove(fd);
        //nameFdTable.values().remove(fd); //delete nameFdTable entry
        System.out.println(fd + "is closed successfully.");
        return 0;
	 }

     /**
     * Delete a file from the file system. If no processes have the file open, the
     * file is deleted immediately and the space it was using is made available for
     * reuse.
     *
     * If any processes still have the file open, the file will remain in existence
     * until the last file descriptor referring to it is closed. However, creat()
     * and open() will not be able to return new file descriptors for the file
     * until it is deleted.
     *
     * Returns 0 on success, or -1 if an error occurred.
     */
	 private int handleUnlink(int fileNameVR){
	 	String fileName = readVirtualMemoryString(fileNameVR, maxFileNameLength);
	 	if (nameFdTable.containsKey(fileName)) {
	 		int fd = nameFdTable.get(fileName);
	 		fdFileTable.remove(fd);
	 		nameFdTable.remove(fileName);
	 	}
	 	if (ThreadedKernel.fileSystem.remove(fileName))
	 		return 0;
	 	else
	 		return -1;
	 }
	 public void setPid(int pid){
	 	this.pid = pid;
	 }
	 public void setPPid(int pPid){
	 	this.pPid = pPid;
	 }
	 public void addChildProcess(int cpid){
		cPids.add(cpid);
	 }

	/**
	 * Execute the program stored in the specified file, with the specified
	 * arguments, in a new child process. The child process has a new unique
	 * process ID, and starts with stdin opened as file descriptor 0, and stdout
	 * opened as file descriptor 1.
	 *
	 * exec() returns the child process's process ID, which can be passed to
	 * join(). On error, returns -1.
	 */
	 private int handleExec(int fileNameVR, int argc, int argvVR){
		//The definition of EXEC in c: int exec(char *file, int argc, char *argv[]);

		//check fileName
		String fileName = readVirtualMemoryString(fileNameVR, maxFileNameLength);
		if (fileName == null)
			return -1;
		int lenFileName = fileName.length();
		//System.out.println("the file name end with" + fileName.substring(lenFileName - 5,lenFileName));
		//System.out.println("file name matched with .coff" + fileName.substring(lenFileName - 5,lenFileName).equals(".coff"));
		if (lenFileName <= 5 || !fileName.substring(lenFileName - 5,lenFileName).equals(".coff"))
			return -1;

		//check argc
		if (argc < 0)
			return -1;
		 String [] argv;
		//check argv
		 if (argc == 0)
			argv = new String[0];
		 else
		 	 argv = new String[argc];
		int argvVRi;
		for (int i = 0; i < argc; i++){
			argvVRi = argvVR + 4*i;
			argv[i] = readVirtualMemoryString(argvVRi, maxArgvLength);
		}

		System.out.println("the arguments are:"+ argv + argc + "in total");

		 UserProcess process = newUserProcess();

         int cPid = process.pid;
		 pidTable.put(cPid, process);
		 process.setPid(cPid);
		 process.setPPid(pid);
		 addChildProcess(cPid);

		 Lib.assertTrue(process.execute(fileName, argv));

		 //KThread.currentThread().finish();
		 return cPid;
 	}

	/**
	 * Suspend execution of the current process until the child process specified
	 * by the processID argument has exited. If the child has already exited by the
	 * time of the call, returns immediately. When the current process resumes, it
	 * disowns the child process, so that join() cannot be used on that process
	 * again.
	 *
	 * status points to an integer where the exit status of the child process will
	 * be stored. This is the value the child passed to exit(). If the child exited
	 * because of an unhandled exception, the value stored is not defined.
	 *
	 * If the child exited normally, returns 1. If the child exited as a result of
	 * an unhandled exception, returns 0. If processID does not refer to a child
	 * process of the current process, returns -1.
	 */
	private int handleJoin(int cPid, int statusVR){
		//int join(int pid, int *status);

		//If processID does not refer to a child  process of the current process, returns -1.
		if (!cPids.contains(cPid) || !pidTable.containsKey(cPid))
			return -1;

		System.out.println("Join");
		pidTable.get(cPid).uThread.join();

		//Get child status and write it to *status
		int status = pidTable.get(cPid).exitStatus;

		byte [] statusByteArr = ByteBuffer.allocate(4).putInt(status).array();
		System.out.println("The status of the child" + status + statusByteArr);
		// not sure the length of the status
		writeVirtualMemory(statusVR, statusByteArr);

		//If the child exited normally, returns 1.
		if (status == 0)
			return 1;

        //If the child exited as a result of an unhandled exception, returns 0.
        if (status != 0)
            return 0;

		return -1;
	}

	/**
	 * Terminate the current process immediately. Any open file descriptors
	 * belonging to the process are closed. Any children of the process no longer
	 * have a parent process.
	 *
	 * status is returned to the parent process as this process's exit status and
	 * can be collected using the join syscall. A process exiting normally should
	 * (but is not required to) set status to 0.
	 *
	 * exit() never returns.
	 */
	private int handleExit(int status){
//		void exit(int status)
		fdFileTable.clear();
		nameFdTable.clear();

		//System.out.println("Total key value pairs in HashMap after cleanning are : " + fdFileTable.size() + fdTable.size());

		unloadSections();
		coff.close();

		    //save the status for parent;
		exitStatus = status;
		System.out.println("The status"+ exitStatus);
		//What I recommend doing is checking if the status of the child is null or not (null indicates abnormal exit,
		// else it is the value of the status the child passed in when calling exit).
		// This requires the child knowing whether it is exiting normally or abnormally when calling exit().

		// Any children of the process no longer have a parent process.
		for (int cPid : cPids)
			pidTable.get(cPid).setPPid(0);

		//In case of last process, call kernel.kernel.terminate()
		pidTable.remove(pid);
		if (pidTable.size() == 0)
			Kernel.kernel.terminate();

		//Wake up parent if sleeping
		uThread.finish();
		//Close Kthread by calling Kthread.finish()
		KThread.finish();
		return -1;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 *
	 * @param syscall the syscall number.
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallCreate:
				return handleCreate(a0);
			case syscallOpen:
				return handleOpen(a0);
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallWrite:
				return handleWrite(a0, a1, a2);
			case syscallClose:
				return handleClose(a0);
			case syscallUnlink:
				return handleUnlink(a0);

			 case syscallExec:
			 	return handleExec(a0, a1, a2);
			 case syscallJoin:
			 	return handleJoin(a0, a1);
			 case syscallExit:
			 	return handleExit(a0);


			default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
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
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private Map<Integer, OpenFile> fdFileTable; //Open File Descriptor Table 16 + 2

	private Map<String, Integer> nameFdTable; //File name to fd

    private static final int maxFileNameLength = 256;

    private static final int localBufferSize = 1024;

    private static final int maxNumOpenFile = 2 + 16;

    private static final int maxArgvLength = 4;

	private static int numProcess = 1;

	private int pid;

	private int pPid = 0;

	private LinkedList<Integer>	cPids = new LinkedList<>();

	private Map<Integer, UserProcess> pidTable = new HashMap<>(); //pid -> Process

	private UThread uThread;

	private int exitStatus;

}
