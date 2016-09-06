#include <sys/stat.h>

int isFdExists(int fd) {
    struct stat statbuf;
    fstat(fd, &statbuf);
    return (statbuf.st_nlink > 0);
}