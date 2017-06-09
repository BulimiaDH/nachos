#include "syscall.h"
#include "stdio.h"

int main()
{
    int fd1 = open("test_doc");
    printf("test_doc opened, fd is %d\n", fd1);
    
    char rd [3000];
    int chRead = read(fd1, rd, 3000);

    //printf(rd);
    
    write(1, rd, chRead);
    //fflush(1);

    printf("\nch read is %d\n", chRead);
    close(fd1);
    


    //added
//    printf( "Checking to see if we print less than count charsi\n" );
//
//    int fd2 = open("3charfile");
//    printf("3charfile opened, fd is %d\n", fd2);
//    char rd2[3000];
//    int chRead2 = read(fd2, rd2, 3000);
//
//    //printf(rd2);
//    write(1, rd2, chRead2);
//    printf("\nch read 2 is %d\n", chRead2);
}

