/*
 * exec2.c
 *
 * Test that we eventually hit a maximum amount of alive processes,
 * and collapse correctly.
 */

#include "syscall.h"
#include "stdio.h"

int
main (int argc, char *argv[])
{
    
    char *prog = "exec2.coff";
    int pid, status, r = 0;
    pid = exec (prog, 0, 0);
  
    if (pid < 0) {
      printf("Hit memory limit!\n");
    }
    else {
      join(pid, &status);
    }
    printf("This process created child %d\n", pid);

}
