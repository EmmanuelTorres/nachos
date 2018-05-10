/* A simple server in the internet domain using TCP
   The port number is passed as an argument */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <pthread.h>
#include <unistd.h>

void error(char *msg)
{
    perror(msg);

}

int main(int argc, char *argv[])
{
     int sockfd, newsockfd, portno;
     unsigned int clilen;
     char buffer[256];
     struct sockaddr_in serv_addr, cli_addr;
     int n;

     // create the socket
     sockfd = socket(AF_INET, SOCK_STREAM, 0);
     if (sockfd < 0)
        error("ERROR opening socket");

     // create a "struct sockaddr_in" to specify the server's IP, port, proto
     bzero((char *) &serv_addr, sizeof(serv_addr));
     portno = 15;
     serv_addr.sin_family = AF_INET;
     serv_addr.sin_addr.s_addr = INADDR_ANY;
     serv_addr.sin_port = htons(portno);

     // bind the socket to the address in the "struct sockaddr_in"
     if (bind(sockfd, (struct sockaddr *) &serv_addr,
              sizeof(serv_addr)) < 0)
              error("ERROR on binding");

     // tell the OS I'm willing to accept connections on the socket
     listen(sockfd,5);

     // wait for a connection to arrive from a client
     clilen = sizeof(cli_addr);
     newsockfd = accept(sockfd,
                 (struct sockaddr *) &cli_addr,
                 &clilen);
     if (newsockfd < 0)
          error("ERROR on accept");

	while(1){
     // read some data from the client
    // bzero(buffer,256);
     n = read(newsockfd,buffer,255);
     if (n < 0) error("ERROR reading from socket");
	 if( strcmp(buffer,".") == 0){
			break;
			return 0;
		}
     // print out the message
     printf("Here is the message: '%s'\n",buffer);

     // write a response to the client
     n = write(newsockfd, buffer, strlen(buffer) );
	 bzero(buffer,256);
     if (n < 0) error("ERROR writing to socket");
     // close the socket  (optional; OS will do when process terminates)

	}
	close(sockfd);
     return 0;
}
