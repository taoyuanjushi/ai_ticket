import logging

from app.core.config import settings

LOG_FORMAT = "%(asctime)s %(levelname)s [%(name)s] %(message)s"
_LOGGING_CONFIGURED = False


def setup_logging() -> None:
    global _LOGGING_CONFIGURED

    level = getattr(logging, settings.log_level.upper(), logging.INFO)
    if not _LOGGING_CONFIGURED:
        logging.basicConfig(level=level, format=LOG_FORMAT)
        _LOGGING_CONFIGURED = True

    logging.getLogger().setLevel(level)


def get_logger(name: str) -> logging.Logger:
    setup_logging()
    return logging.getLogger(name)
