FROM openvidu/openvidu-server-kms:2.6.0
RUN apt-get update
RUN apt-get install -y git
RUN apt-get install -y nodejs npm
RUN apt-get install -y curl
RUN curl -sL https://deb.nodesource.com/setup_8.x | bash - \
    && apt-get install -y nodejs
RUN npm install -g @angular/cli@7.1.3
RUN npm install -g http-server
COPY entrypoint.sh .
EXPOSE 4443
EXPOSE 5000

ENTRYPOINT ["/bin/sh", "-c", "./entrypoint.sh"]
