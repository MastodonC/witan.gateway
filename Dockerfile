FROM phusion/baseimage:0.9.17
MAINTAINER Antony Woods <antony@mastodonc.com>

CMD ["/sbin/my_init"]

RUN apt-get update && apt-get install -y software-properties-common

# Install Java
RUN add-apt-repository -y ppa:webupd8team/java \
&& apt-get update \
&& echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | sudo /usr/bin/debconf-set-selections \
&& apt-get install -y \
software-properties-common \
oracle-java8-installer

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

EXPOSE 30015

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
