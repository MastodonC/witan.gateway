FROM zittix/docker-baseimage-java8
MAINTAINER Antony Woods <antony@mastodonc.com>

CMD ["/sbin/my_init"]

RUN apt-get update && apt-get install -y software-properties-common python2.7 unzip

# Install Nginx.
RUN apt-get install -y python-software-properties && \
add-apt-repository -y ppa:nginx/stable && \
apt-get update && \
apt-get install -y nginx && \
rm -rf /var/lib/apt/lists/* && \
echo "\ndaemon off;" >> /etc/nginx/nginx.conf && \
chown -R www-data:www-data /var/lib/nginx

RUN mkdir /etc/service/gateway
RUN mkdir /etc/service/nginx

ADD target/witan.gateway-standalone.jar /srv/witan.gateway.jar

ADD scripts/run.sh /etc/service/gateway/run
ADD scripts/nginx.sh /etc/service/nginx/run

ENV SECRETS_BUCKET=$SECRETS_BUCKET
ENV AWS_REGION=$AWS_REGION

ADD scripts/download-secrets.sh /root/download-secrets.sh

EXPOSE 30015
EXPOSE 80

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
