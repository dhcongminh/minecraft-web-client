FROM coturn/coturn:4.6.3-r2
USER root
RUN apt-get update && apt-get install -y --no-install-recommends gettext-base && rm -rf /var/lib/apt/lists/*
COPY entrypoint.sh /entrypoint.sh
COPY turnserver.conf.template /etc/coturn/turnserver.conf.template
RUN chmod 755 /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
