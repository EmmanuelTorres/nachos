#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netdb.h>

void error(char *msg)
{
    perror(msg);
    exit(0);
}

int unlimitedSZMessage(int sockfd){
	unsigned int len_max = 1;
    unsigned int current_size = 0;

    char *pStr = malloc(len_max);
    current_size = len_max;

    if(pStr != NULL)
    {
	int c = EOF;
	unsigned int i =0;
        //accept user input until hit enter or end of file
	while (( c = getchar() ) != '\n' && c != EOF)
	{
		pStr[i++]=(char)c;

		//if i reached maximize size then realloc size
		if(i == current_size)
		{
            current_size = i+len_max;
			pStr = realloc(pStr, current_size);

		}

	}

	pStr[i] = '\0';

	// write the message to the destination
	if( strcmp(pStr,".")==0){
			pStr = "User has disconnected";
			write(sockfd,pStr,22);

			close(sockfd);
			return 0;
		}
	int n = write (sockfd,pStr,current_size);

     //free it
	if (n < 0)
		error("ERROR writing to socket");

	n = read(sockfd,pStr,255);//current_size);

	if (n < 0)
        error("ERROR reading from socket");
	 printf("%s\n",pStr);

	free(pStr);
	pStr = NULL;


    }
	return 1;
}

int main(int argc, char *argv[])
{
    int sockfd, portno, n;
    struct sockaddr_in serv_addr;
    struct hostent *server;



    // make sure we're invoked with destination host
    if (argc < 2) {
       fprintf(stderr,"usage %s hostname\n", argv[0]);
       exit(0);
    }

    portno = 15; // take the port number

    // create a socket
    sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd < 0)
        error("ERROR opening socket");
    // convert the destination hostname to an IP address
    server = gethostbyname(argv[1]); // take the first argent as the ip address you want to connect to
    if (server == NULL) {
        fprintf(stderr,"ERROR, no such host\n");
        exit(0);
    }
    // create a "struct sockaddr_in" with proto, IP, port of destination
    bzero((char *) &serv_addr, sizeof(serv_addr));
    serv_addr.sin_family = AF_INET;
    bcopy((char *)server->h_addr,
         (char *)&serv_addr.sin_addr.s_addr,
         server->h_length);
    serv_addr.sin_port = htons(portno);

    // connect our socket to that destination
    if (connect(sockfd, (struct sockaddr *) &serv_addr, sizeof(serv_addr)) < 0)
        error("ERROR connecting");

	while (1) {
		printf("Please enter the message: ");

		int close = unlimitedSZMessage(sockfd);
		if(close == 0)
			break;




	}

    return 0;
}
