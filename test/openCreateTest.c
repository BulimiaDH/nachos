#include "syscall.h"
#include "stdio.h"

int
main()
{
    int fd1 = open("test_doc");
    int fd2 = creat("test1_doc");
    //printf("test_doc opened, fd is %d\n", fd1);
    /* not reached */

}
