FROM coturn/coturn:4.6.3-r2
USER root
RUN apk add --no-cache gettext
COPY entrypoint.sh /entrypoint.sh
COPY turnserver.conf.template /etc/coturn/turnserver.conf.template
RUN chmod 755 /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
