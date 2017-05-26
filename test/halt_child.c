/* halt_child.c
 *	Simple program to test that child processes can't halt the machine.
 *	
 * 	NOTE: for some reason, user programs with global data structures 
 *	sometimes haven't worked in the Nachos environment.  So be careful
 *	out there!  One option is to allocate data structures as 
 * 	automatics within a procedure, but if you do this, you have to
 *	be careful to allocate a big enough stack to hold the automatics!
 */

#include "syscall.h"

int
main()
{
    char *prog = "halt.coff";
    printf("Child exit1.coff ...\n");
    exec(prog, 0, 0);
    printf("This is the parent\n");
}
