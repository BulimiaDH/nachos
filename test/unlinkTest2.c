#include "syscall.h"
#include "stdio.h"
#include "stdlib.h"

int main()
{
    int fd1, fd2, fd3, rs1;
    printf("== Basic unlink test 1 ==\n");
    printf("Create and unlink a test file\n");

    fd1 = creat("test");
    printf("empty file 'test' created. fd is %d\n", fd1);

    rs1 = unlink("test");
    printf("unlink 'test' created. result is %d\n", rs1);

    fd2 = open("test");
    if (fd2 > 1) printf("Test FAILED\n");
    else printf("Can't open test again. --> Test PASSING\n");

    fd3 = creat("test");
    if (fd3 == fd1) printf("FD recycled --> Test PASSING\n");
    unlink("test");



    printf("\n== Basic unlink test 2 ==\n");
    printf("Create + open a file to see if unlink recycles all FDs\n");

    fd1 = creat("test");
    printf("empty file 'test' created. fd is %d\n", fd1);

    fd2 = open("test");
    printf("open 'test' again. fd is %d\n", fd2);

    rs1 = unlink("test");
    printf("unlink 'test' created. result is %d\n", rs1);

    if (open("test") < 0) printf("successfully unlinked\n");

    /*  
    if (write(fd2, "hi", 2) == -1) printf("\nCan't write using a closed fd --> Test PASSING\n");
    else printf("\nCan write via closed fd --> Test FAILED\n");
    

    fd3 = creat("test");
    if (fd3 == fd1) {
        printf("create 'test' again, fd is %d\n", fd1);
        printf("Successfully recycle all fds --> test PASSING\n");
    }
    else {
        printf("Test FAILED\n");
    }
    */

    //Added

    if( unlink("randomName") < 0 ){
        printf("cannot unlink an invalid file --> test PASSING\n");
    }
    else
        printf("can unlink an invalid file --> test FAILED\n");

    //input size > 256b
    char str[257];
    memset( str, 'a', 257 );
    str[256] = '\0';
    printf( "sizeof str is %d\n", sizeof(str)  );
    

    if( unlink( str ) < 0 )
        printf("cannot unlink overly large input --> test PASSING" );
    else
        printf("can unlink overly large input --> test FAILED" );

//    int fd3 = open("test");
//    printf("open test again, fd is %d\n", fd3);
//
//    close(fd1);
//    printf("close test_doc now\n");
//    int fd4 = creat("test2");
//    printf("create 'test2', fd is %d\n", fd4);

    /* not reached */
}

