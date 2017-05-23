/* halt.c
 *	Simple program to test whether running a user program works.
 *	
 *	Just do a "syscall" that shuts down the OS.
 *
 * 	NOTE: for some reason, user programs with global data structures 
 *	sometimes haven't worked in the Nachos environment.  So be careful
 *	out there!  One option is to allocate data structures as 
 * 	automatics within a procedure, but if you do this, you have to
 *	be careful to allocate a big enough stack to hold the automatics!
 */

#include "syscall.h"
#include "stdio.h"

int
main()
{
    char *fname = "write.out";
    int fd = open (fname);
    char *buffer;
    printf ("reading %s into buffer...\n", fname);
    int len = 10;
    int r = read (fd, buffer, len);
    if (r < 0) {
        printf ("...failed (r = %d)\n", r);
    } else if (r != len) {
        printf ("...failed (expected to read %d bytes, but read %d)\n", len, r);
        printf("the content of the read %s", buffer);
    } else {
        printf ("...success\n");
        printf("the content of the read %s", buffer);
    }
    close(fd);
    return;
}
