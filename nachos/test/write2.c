/*
 * write1.c
 *
 * Write a string to stdout, one byte at a time.  Does not require any
 * of the other system calls to be implemented.
 *
 * Geoff Voelker
 * 11/9/15
 */

#include "syscall.h"

int main (int argc, char *argv[])
{

    int fd2 = open("test_doc");
    int fd4 = open("test2_doc");
    int fd5 = creat("test1_doc"); // the file is already exist when create?

    char *str = "\nroses are red\nviolets are blue\nI love Nachos\nand so do you\n\n";

//    int r = write (2, str, 10);
//    if (r != 10) {
//        printf ("fd1:failed to write character (r = %d)\n", r);
//        //exit (-1);
//    }
//    else{
//        printf ("fd1:succeed to write character (r = %d)\n", r);
//    }
//    close(2);

    int r = read(2, str, 10);
    printf("what i read from test_doc %s\n", str);
    if (r != 10) {
        printf ("fd1:failed to read character (r = %d)\n", r);
        //exit (-1);
    }
    else{
        printf ("fd1:succeed to read character (r = %d)\n", r);
    }


    close(2);
    close(3);
    close(4);

//    while (*str) {
//	int r = write (1, str, 1);
//	if (r != 1) {
//	    printf ("failed to write character (r = %d)\n", r);
//	    exit (-1);
//	}
//	str++;
//    }

    return 0;
}