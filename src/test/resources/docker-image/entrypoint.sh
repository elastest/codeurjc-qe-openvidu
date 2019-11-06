echo $OPENVIDU_PUBLICURL;

echo 'run supervisord';
/usr/bin/supervisord & echo '##### BUILD OPENVIDU #####';

git clone https://github.com/elastest/openvidu-loadtest;
cd openvidu-loadtest/webapp
openssl req -newkey rsa:2048 -new -nodes -x509 -days 3650 -subj '/CN=www.mydom.com/O=My Company LTD./C=US' -keyout key.pem -out cert.pem;

echo '##### RUN OPENVIDU #####';
http-server -S -p 5000;




