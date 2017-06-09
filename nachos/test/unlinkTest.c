#include "syscall.h"
#include "stdio.h"

int main()
{
    int fd1, fd2, fd3, rs1, rs2;
    char arr[4];
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
    printf("create test again, fd is %d\n", fd3);
//    if (fd3 == fd1) printf("FD recycled --> Test PASSING\n");
    unlink("test");



    printf("\n== Basic unlink test 2 ==\n");
    printf("Create + open a file to see if unlink recycles all FDs\n");

    fd1 = creat("test");
    printf("empty file 'test' created. fd is %d\n", fd1);

    fd2 = open("test");
    printf("open 'test' again. fd is %d\n", fd2);

    write(fd1,"hihi",4);

    rs1 = unlink("test");
    printf("unlink 'test'. result is %d\n", rs1);

    if (open("test") < 0) printf("successfully unlinked\n");

    printf("try to read the content of test file\n");
    rs1 = read(fd1, arr, 4);
    if (rs1 != -1) {
        printf("--> FD can still be read AFTER unlink: ");
        printf(arr);
        printf("\n");
    }
    else {
        printf("--> FD cannot be read AFTER unlink\n");
    }
    rs2 = read(fd2, arr, 4);
    if (rs2 != -1) {
            printf("--> FD can still be read AFTER unlink: ");
            printf(arr);
            printf("\n");
        }
        else {
            printf("--> FD cannot be read AFTER unlink\n");
        }
    printf("characters read is %d", rs2);

    if (write(fd2, "hi", 2) == -1) printf("\n--> Can't write using a unlinked fd %d\n", fd2);
    else printf("\n--> Can write via closed fd %d\n", fd2);

    if (write(fd1, "hi", 2) == -1) printf("--> Can't write using a unlinked fd %d\n", fd1);
    else printf("\n--> Can write via closed fd %d\n", fd1);

    fd3 = creat("test");
    printf("create 'test' again, fd is %d\n", fd3);
//    if (fd3 == fd1) {
//        printf("Successfully recycle all fds --> test PASSING\n");
//    }
//    else {
//        printf("Test FAILED\n");
//    }
    unlink("test");


    printf("\n== unlink basic test 3 ==\n");
    printf("check if the fd can still be used after unlink without closing\n");
    fd1 = creat("test");
    printf("test file created\n");
    rs1 = write(fd1, "hihi", 4);
    printf("wrote 'hihi' into test file\n");
    unlink("test");
    printf("test file unlinked\n");
//    char arr[4];
    printf("try to read the content of test file\n");
    rs1 = read(fd1, arr, 4);
    if (rs1 != -1) {
        printf("--> FD can still be used AFTER unlink:\n");
        printf(arr);
        printf("\n");
    }
    else {
        printf("--> FD cannot be used AFTER unlink\n");
    }
    printf("characters read is %d", rs1);

//    int fd3 = open("test");
//    printf("open test again, fd is %d\n", fd3);
//
//    close(fd1);
//    printf("close test_doc now\n");
//    int fd4 = creat("test2");
//    printf("create 'test2', fd is %d\n", fd4);

    /* not reached */
}

