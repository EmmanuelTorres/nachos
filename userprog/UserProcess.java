package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.io.EOFException;
import java.util.HashMap;

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
		processIdLock.acquire();
		processId = nextProcessId++;
		runningProcesses++;
		processIdLock.release();
		
		// stdin/stdout
		fileDescriptors[0] = UserKernel.console.openForReading();
		FileRef.referenceFile(fileDescriptors[0].getName());
		fileDescriptors[1] = UserKernel.console.openForWriting();
		FileRef.referenceFile(fileDescriptors[1].getName());
	}
	
	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 *
	 * @return a new process of the correct class.
	 */
	static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}
	
	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 *
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;
		new UThread(this).setName(name).fork();
		
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
	 * A helper function to determine whether a virtual address is within
	 * the bounds of our process' pageTable
	 * @param vaddr The virtual address we want to check
	 * @return true if we're within the bounds of pages
	 */
	private boolean withinPageBounds(int vaddr) {
		int pageNumber = Processor.pageFromAddress(vaddr);

		return pageNumber >= 0 && pageNumber < numPages;
	}

	/**
	 * A helper function to determine whether a fileDescriptor is within
	 * the bounds of our fileDescriptors array
	 * @param fileDescriptorIndex The file descriptor index we want to check
	 * @return true if we're within the bounds of the file descriptor and the index isn't null
	 */
	private boolean getFileDescriptor(int fileDescriptorIndex) {
		 return fileDescriptorIndex >= 0 && fileDescriptorIndex < FILE_DESCRIPTORS_SIZE
				&& fileDescriptors[fileDescriptorIndex] != null;
	}

	/**
	 * A helper function to get the next available file descriptor
	 * @return index of an available file descriptor on success, -1 on error
	 */
	private int getAvailableFileDescriptor() {
		for(int i = 0; i < FILE_DESCRIPTORS_SIZE; i++) {
			if(fileDescriptors[i] == null)
				return i;
		}
		return -1;
	}

	/**
	 * A helper function to get a page table entry from our page table
	 * @param virtualPageNumber The page table entry index we want to check
	 * @return TranslationEntry if the page table is populated and the index is within bounds
	 */
	private TranslationEntry getPageTableEntry(int virtualPageNumber) {
		if (pageTable.length == 0 || !withinPageBounds(virtualPageNumber)) {
			return null;
		}

		return pageTable[virtualPageNumber];
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 *
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including
	 *            the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
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
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] physicalMemory = Machine.processor().getMemory();

		// Return this value
		int bytesTransferred = 0;

		// Grab the virtualPageNumber and offset size
		int virtualPageNumber = vaddr / pageSize;
		int virtualOffset = vaddr % pageSize;

		// We return 0 if the page table entry is null or invalid
		TranslationEntry entry = getPageTableEntry(virtualPageNumber);
		if (entry == null || !entry.valid) {
			return bytesTransferred;
		}

		entry.used = true;

		int physicalAddress = entry.ppn * pageSize + virtualOffset;

		// Set our bytesTransferred to the amount of bytes we hope to transfer with
		// the arraycopy call in the next line. We need the Math.min to not exceed bounds
		bytesTransferred = Math.min(length, physicalMemory.length - physicalAddress);

		System.arraycopy(physicalMemory, physicalAddress, data, offset, bytesTransferred);

		return bytesTransferred;
	}
	
	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

		byte[] physicalMemory = Machine.processor().getMemory();

		// Return this value
		int bytesTransferred = 0;

		// Grab the virtualPageNumber and offset size
		int virtualPageNumber = vaddr / pageSize;
		int virtualOffset = vaddr % pageSize;

		// We return 0 if the page table entry is null or invalid or is read only
		TranslationEntry entry = getPageTableEntry(virtualPageNumber);
		if (entry == null || !entry.valid || entry.readOnly) {
			return bytesTransferred;
		}

		entry.used = true;

		int physicalAddress = entry.ppn * pageSize + virtualOffset;

		// Set our bytesTransferred to the amount of bytes we hope to transfer with
		// the arraycopy call in the next line. We need the Math.min to not exceed bounds
		bytesTransferred = Math.min(length, physicalMemory.length - physicalAddress);

		System.arraycopy(data, offset, physicalMemory, physicalAddress, bytesTransferred);

		return bytesTransferred;
	}
	
	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 *
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	protected boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
		
		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}
		
		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
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
		// Fill the pageTable with the number of pages we need
		pageTable = ((UserKernel) Kernel.kernel).acquirePages(numPages);

		for (int i = 0; i < pageTable.length; i++) {
			pageTable[i].vpn = i;
		}

		for (int sectionNumber = 0; sectionNumber < coff.getNumSections(); sectionNumber++) {
			CoffSection section = coff.getSection(sectionNumber);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			int vpn = section.getFirstVPN();
			for (int i = 0; i < section.getLength(); i++) {
				section.loadPage(i, pageTable[i + vpn].ppn);
			}
		}
		
		return true;
	}
	
	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		((UserKernel) Kernel.kernel).releasePages(pageTable);
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
		for (int i = 0; i < Processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);
		
		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);
		
		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}
	
	private int openFile(int fileNamePtr, boolean create) {
		if (!withinPageBounds(fileNamePtr))
			return terminate();
		
		// Try to get an entry in the file table
		int fileDesc = getAvailableFileDescriptor();
		if (fileDesc == -1)
			return -1;
		
		String fileName = readVirtualMemoryString(fileNamePtr, MAX_STRING_LENGTH);
		
		// Attempt to add a new reference to this file
		if (!FileRef.referenceFile(fileName))
			return -1; // Cannot make new references to files that are marked
		// for deletion
		
		// Attempt to actually open the file
		OpenFile file = UserKernel.fileSystem.open(fileName, create);
		if (file == null) {
			// Remove the previously created reference since we failed to open
			// the file
			FileRef.unreferenceFile(fileName);
			return -1;
		}
		
		// Store the file in our file table
		fileDescriptors[fileDesc] = file;
		
		return fileDesc;
	}
	
	/**
	 * Informs the parent process that the child has exited
	 *
	 * @param childProcessId The process id of the child
	 * @param childStatus The status of the child
	 */
	private void setChildExitStatus(int childProcessId, Integer childStatus) {
		// If the child process doesn't belong to our children, return
		if (!children.containsKey(childProcessId)) {
			return;
		}

		ChildProcess child = children.get(childProcessId);

		// If the child is null, return
		if (child == null) {
			return;
		}

		// Set the child's process to null
		child.process = null;

		// Set the child's return value to what they pass to us
		child.returnValue = childStatus;
	}
	
	/**
	 * Terminate this process due to unhandled exception
	 */
	private int terminate() {
		handleExit(null);
		return -1;
	}

	/**
	 * Cause caller to sleep until this process has isRunning
	 */
	//
	private void waitForExit() {
	}
	
	private static final int
			syscallHalt = 0,
			syscallExit = 1,
			syscallExec = 2,
			syscallJoin = 3,
			syscallCreate = 4,
			syscallOpen = 5,
			syscallRead = 6,
			syscallWrite = 7,
			syscallClose = 8,
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
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallHalt:
				return handleHalt();
			case syscallExit:
				return handleExit(a0);
			case syscallExec:
				return handleExec(a0, a1, a2);
			case syscallJoin:
				return handleJoin(a0, a1);
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
			
			default:
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Halt the Nachos machine by calling Machine.halt(). Only the root process
	 * (the first process, executed by UserKernel.run()) should be allowed to
	 * execute this syscall. Any other process should ignore the syscall and return
	 * immediately.
	 * @return 0
	 */
	private int handleHalt() {
		if (processId != 0) {
			return 0;
		}

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");

		return 0;
	}

	/**
	 * Terminate the current process immediately. Any open file descriptors
	 * belonging to the process are closed. Any children of the process no longer
	 * have a parent process.
	 *
	 * @param status is returned to the parent process as this process's exit status and
	 *               can be collected using the join syscall. A process exiting normally should
	 *               (but is not required to) set status to 0.
	 * @return 0, but this function never returns
	 */
	private int handleExit(Integer status) {
		// Any open file descriptors belonging to the process are closed
		for (int i = 0; i < FILE_DESCRIPTORS_SIZE; i++) {
			if (getFileDescriptor(i)) {
				handleClose(i);
			}
		}

		// Any children of the process no longer have a parent process
		for (ChildProcess child : children.values()) {
			if (child.process != null) {
				child.process.parent = null;
			}
		}
		children = null;

		unloadSections();

		// Attempt to inform our parent that we're exiting
		if (parent != null) {
			parent.setChildExitStatus(processId, status);
		}

		joinSem.V();

		// If the processId is 0, we're the kernel so we terminate ourselves
		if (processId == 0) {
			Kernel.kernel.terminate();
		}

		// Terminate current thread
		KThread.finish();

		return 0;
	}


	/**
	 * Execute the program stored in the specified file, with the specified
	 * arguments, in a new child process. The child process has a new unique
	 * process ID, and starts with stdin opened as file descriptor 0, and stdout
	 * opened as file descriptor 1.
	 *
	 * @param file a null-terminated string that specifies the name of the file
	 *                    containing the executable. Note that this string must include the ".coff"
	 *                    extension.
	 * @param argc the number of arguments to pass to the child process. This
	 *                number must be non-negative.
	 * @param argv an array of pointers to null-terminated strings that represent the
	 *                arguments to pass to the child process. argv[0] points to the first
	 *                argument, and argv[argc-1] points to the last argument.
	 * @return the child process's process id, which can be passed to join(). On error, returns -1.
	 */
	private int handleExec(int file, int argc, int argv) {
		if (!withinPageBounds(file) || !withinPageBounds(this.argv)) {
			return terminate();
		}

		// A null-terminated string that specifies the name of the file containing the executable
		String fileName = readVirtualMemoryString(file, MAX_STRING_LENGTH);

		// This string must include the ".coff" extension
		if (fileName == null || !fileName.endsWith(".coff")) {
			return -1;
		}

		// The arguments to pass to the child process
		String arguments[] = new String[argc];

		// Load in those arguments
		for (int i = 0; i < argc; i++) {
			byte[] argAddress = new byte[4];

			readVirtualMemory(argv + i * 4, argAddress);

			// read string argument at pointer from above
			arguments[i] = readVirtualMemoryString(Lib.bytesToInt(argAddress,0), MAX_STRING_LENGTH);
		}

		// The new child process
		UserProcess newChild = newUserProcess();
		newChild.parent = this;
		children.put(newChild.processId, new ChildProcess(newChild));

		// Have the child process execute the program
		newChild.execute(fileName, arguments);

		return newChild.processId;
	}

	/**
	 * Suspend execution of the current process until the child process specified
	 * by the processID argument has exited. If the child has already exited by the
	 * time of the call, returns immediately. When the current process resumes, it
	 * disowns the child process, so that join() cannot be used on that process
	 * again.
	 *
	 * @param childProcessId the process id of the child process, returned by exec().
	 * @param status points to an integer where the exit status of the child process will
	 *                     be stored. This is the value the child passed to exit(). If the child exited
	 *                     because of an unhandled exception, the value stored is not defined.
	 * @return If the child exited normally, returns 1. If the child exited as a result of
	 *              an unhandled exception, returns 0. If processID does not refer to a child
	 *              process of the current process, returns -1.
	 */
	private int handleJoin(int childProcessId, int status) {
		if (!withinPageBounds(status)) {
			terminate();
		}

		// If the process isn't a child, return -1
		if (!children.containsKey(childProcessId)) {
			return -1;
		}

		// At this point, child itself cannot be null
		// However, the child's process can be null
		ChildProcess child = children.get(childProcessId);

		if (child.process != null) {
			child.process.joinSem.P();
		}

		// Remove the isRunning process from our list of children
		children.remove(childProcessId);

		if (child.returnValue == null) {
			return 0;
		}

		// Write the child's return value into the status
		writeVirtualMemory(status, Lib.bytesFromInt(child.returnValue));

		return 1;
	}

	/**
	 * Handles creat(char* fileName) system call
	 * @param fileNamePtr The address of the file we're supposed to create
	 * @return -1 on error, 0 on success
	 */
	private int handleCreate(int fileNamePtr) {
		return openFile(fileNamePtr, true);
	}

	/**
	 * Handles open(char* fileName) system call
	 * @param fileNamePtr The address of the file we're supposed to open
	 * @return -1 on error, 0 on success
	 */
	private int handleOpen(int fileNamePtr) {
		return openFile(fileNamePtr, false);
	}

	/**
	 * Read data from open file into buffer
	 *
	 * @param fileDesc
	 *            File descriptor
	 * @param bufferPtr
	 *            Pointer to buffer in virtual memory
	 * @param size
	 *            How much to read
	 * @return Number of bytes read, or -1 on error
	 */
	private int handleRead(int fileDesc, int bufferPtr, int size) {
		if (!withinPageBounds(bufferPtr))
			return terminate();
		if (!getFileDescriptor(fileDesc))
			return -1;

		byte buffer[] = new byte[size];
		int bytesRead = fileDescriptors[fileDesc].read(buffer, 0, size);

		// Failed to read
		if (bytesRead == -1)
			return -1;

		int bytesWritten = writeVirtualMemory(bufferPtr, buffer, 0, bytesRead);
		// We weren't able to write the whole buffer to memory!
		if (bytesWritten != bytesRead)
			return -1;

		return bytesRead;
	}

	/**
	 * Write data from buffer into an open file
	 *
	 * @param fileDesc
	 *            File descriptor
	 * @param bufferPtr
	 *            Pointer to buffer in virtual memory
	 * @param size
	 *            Size of buffer
	 * @return Number of bytes successfully written, or -1 on error
	 */

	/**
	 * Handles the write syscall
	 * @param fileDescriptor The index of the fileDescriptor we want to write to
	 * @param bufferPtr The address of where the buffer starts
	 * @param size The size of the buffer in bytes
	 * @return -1 on error, the amount of bytes written on success
	 */
	private int handleWrite(int fileDescriptor, int bufferPtr, int size) {
		if (!withinPageBounds(bufferPtr))
			return terminate();
		if (!getFileDescriptor(fileDescriptor))
			return -1;

		byte buffer[] = new byte[size];
		int bytesRead = readVirtualMemory(bufferPtr, buffer);

		return fileDescriptors[fileDescriptor].write(buffer, 0, bytesRead);
	}

	/**
	 * Close a file and free its place in the file table
	 *
	 * @param fileDescriptor
	 *            Index of file in file table
	 * @return 0 on success, -1 on error
	 */

	/**
	 * Handles the close system call
	 * @param fileDescriptor The index of fileDescriptors we want to close
	 * @return -1 on error, 0 on success
	 */
	private int handleClose(int fileDescriptor) {
		if (!getFileDescriptor(fileDescriptor))
			return -1;

		String fileName = fileDescriptors[fileDescriptor].getName();

		// Remove the file from our file table
		fileDescriptors[fileDescriptor].close();
		fileDescriptors[fileDescriptor] = null;

		// Unreference the file and delete if necessary
		return FileRef.unreferenceFile(fileName);
	}

	/**
	 * Handles the unlink system call
	 * @param fileNamePtr The address of the file we want to unlink
	 * @return 0 on success, -1 on error
	 */
	private int handleUnlink(int fileNamePtr) {
		if (!withinPageBounds(fileNamePtr))
			return terminate();

		String fileName = readVirtualMemoryString(fileNamePtr,
				MAX_STRING_LENGTH);
		return FileRef.deleteFile(fileName);
	}
	
	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exception</tt> constants.
	 *
	 * @param cause
	 *            the user exception that occurred.
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
				
				// Process did something naughty
				terminate();
				
				Lib.assertNotReached("Unexpected exception");
		}
	}
	
	/**
	 * Internal class to keep track of children processes and their exit value
	 */
	private static class ChildProcess {
		public Integer returnValue;
		public UserProcess process;
		
		ChildProcess(UserProcess child) {
			process = child;
			returnValue = null;
		}
	}
	
	/**
	 * Internal class to keep track of how many processes reference a given file
	 */
	protected static class FileRef {
		int references;
		boolean delete;
		
		/**
		 * Increment the number of active references there are to a file
		 *
		 * @return False if the file has been marked for deletion
		 */
		public static boolean referenceFile(String fileName) {
			FileRef ref = updateFileReference(fileName);
			boolean canReference = !ref.delete;
			if (canReference)
				ref.references++;
			finishUpdateFileReference();
			return canReference;
		}
		
		/**
		 * Decrement the number of active references there are to a file Delete
		 * the file if necessary
		 *
		 * @return 0 on success, -1 on failure
		 */
		public static int unreferenceFile(String fileName) {
			FileRef ref = updateFileReference(fileName);
			ref.references--;
			Lib.assertTrue(ref.references >= 0);
			int ret = removeIfNecessary(fileName, ref);
			finishUpdateFileReference();
			return ret;
		}
		
		/**
		 * Mark a file as pending deletion, and delete the file if no active
		 * references
		 *
		 * @return 0 on success, -1 on failure
		 */
		public static int deleteFile(String fileName) {
			FileRef ref = updateFileReference(fileName);
			ref.delete = true;
			int ret = removeIfNecessary(fileName, ref);
			finishUpdateFileReference();
			return ret;
		}
		
		/**
		 * Remove a file if marked for deletion and has no active references
		 * Remove the file from the reference table if no active references THIS
		 * FUNCTION MUST BE CALLED WITHIN AN UPDATEFILEREFERENCE LOCK!
		 *
		 * @return 0 on success, -1 on failure to remove file
		 */
		private static int removeIfNecessary(String fileName, FileRef ref) {
			if (ref.references <= 0) {
				globalFileReferences.remove(fileName);
				if (ref.delete == true) {
					if (!UserKernel.fileSystem.remove(fileName))
						return -1;
				}
			}
			return 0;
		}
		
		/**
		 * Lock the global file reference table and return a file reference for
		 * modification. If the reference doesn't already exist, create it.
		 * finishUpdateFileReference() must be called to unlock the table again!
		 *
		 * @param fileName
		 *            File we with to reference
		 * @return FileRef object
		 */
		private static FileRef updateFileReference(String fileName) {
			globalFileReferencesLock.acquire();
			FileRef ref = globalFileReferences.get(fileName);
			if (ref == null) {
				ref = new FileRef();
				globalFileReferences.put(fileName, ref);
			}
			
			return ref;
		}
		
		/**
		 * Release the lock on the global file reference table
		 */
		private static void finishUpdateFileReference() {
			globalFileReferencesLock.release();
		}
		
		/** Global file reference tracker & lock */
		private static HashMap<String, FileRef> globalFileReferences = new HashMap<String, FileRef>();
		private static Lock globalFileReferencesLock = new Lock();
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
	
	// ----- Our Variables -----
	/** The maximum length of strings passed as arguments to system calls */
	private static final int MAX_STRING_LENGTH = 256;
	
	private static final int FILE_DESCRIPTORS_SIZE = 16;
	
	/** A lock to guarantee unique process ids */
	private static Lock processIdLock = new Lock();
	
	/** The process id, unique for every process */
	private int processId;
	
	/** The next process id to be generated. Static to account for global usage */
	private static int nextProcessId = 0;
	
	/** The parent process of a UserProcess */
	private UserProcess parent;
	/** A processId:UserProcess mapping of children belongs to this UserProcess */
	private HashMap<Integer, ChildProcess> children = new HashMap<Integer, ChildProcess>();
	
	/** Process file descriptor table */
	private OpenFile[] fileDescriptors = new OpenFile[FILE_DESCRIPTORS_SIZE];
	
	/** Join condition */
	private Semaphore joinSem = new Semaphore(0);
	
	/** Number of processes */
	private static int runningProcesses = 0;
}