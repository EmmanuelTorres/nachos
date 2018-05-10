/* A simple server in the internet domain using TCP
   The port number is passed as an argument */
#include <stdio.h>
#include <stdlib.h>
#include <strings.h>
#include <sys/types.h> 
#include <sys/socket.h>
#include <netinet/in.h>
#include <pthread.h>
#include <unistd.h>
#include <string.h>
void error(char *msg)
{
    perror(msg); // throw network error message
}
int array[] = {-5,-4,-3,-2,-1}; // array of size 5 to hold socket file descriptors
int i=0; // index for array

int main(int argc, char *argv[])
{
    int sockfd, newsockfd, portno;
    unsigned int clilen;
    char buffer[256];
	struct sockaddr_in serv_addr, cli_addr;
    int n;

     // create the socket
    sockfd = socket(AF_INET, SOCK_STREAM, 0); // bind a socket fd
    if (sockfd < 0) 
		error("ERROR opening socket");

     // create a "struct sockaddr_in" to specify the server's IP, port, proto
    bzero((char *) &serv_addr, sizeof(serv_addr));
    portno = 15;
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_addr.s_addr = INADDR_ANY;
    serv_addr.sin_port = htons(portno);

     // bind the socket to the address in the "struct sockaddr_in"
    if (bind(sockfd, (struct sockaddr *) &serv_addr,sizeof(serv_addr)) < 0) // try to bind the stuff 
       error("ERROR on binding");

     // tell the OS I'm willing to accept connections on the socket
    listen(sockfd,5); // second argument being the number clients willing to accept
	 
     // wait for a connection to arrive from a client
    clilen = sizeof(cli_addr);
	int child_process;
	while(1){
		newsockfd = accept(sockfd, (struct sockaddr *) &cli_addr,  &clilen);// accept and save it to a new socket file desc
		if (newsockfd < 0)
			error("ERROR on accept");
		array[i]=newsockfd; // assign it to a slot in the array of file desc
		i++;//increment where i is 
		if( (child_process = fork() ) == 0){ // fork a new process so that it can accept another client if needed to
			close(sockfd); //close the previous socket now that is needed 
			while(1){
				read(newsockfd, buffer, 255); // read from the socket and place the message into the buffer 
				if(strcmp(buffer, ".") == 0){ // if the buffer has just a "." just break the loop to disconnect and it will close the socket when it loops around
					printf("Disconnected\n");
					break;
				}
				else{
					printf("Client %d: %s\n",newsockfd, buffer);
					for(int j=0;j<i;j++) // loop through each array and check the file descriptor if it is not the current one route the message to them
						if(array[j]!=newsockfd){ // the idea is the local client keeps a copy of the message they wrote and route their message to everyone else but themselves
							write(array[j],buffer,strlen(buffer));
						}
					bzero(buffer, 255); // clear out the buffer 
				}
				bzero(buffer, 255);				
			}
		}
	}
    return 0;	 
}