from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "ticket-agent-python"
    app_env: str = "dev"
    log_level: str = "INFO"

    llm_api_key: str = ""
    llm_provider: str = "openai_compatible"
    llm_api_base_url: str = ""
    llm_base_url: str = ""
    llm_model: str = ""
    llm_timeout: float = 30
    llm_timeout_seconds: float = 30
    llm_mock_mode: bool = True

    java_api_base_url: str = "http://127.0.0.1:8080"
    java_api_token: str = ""
    java_api_timeout: float = 10

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    @property
    def has_llm_config(self) -> bool:
        return all(
            [
                self.llm_api_key.strip(),
                self.resolved_llm_api_base_url,
                self.llm_model.strip(),
            ]
        )

    @property
    def resolved_llm_api_base_url(self) -> str:
        return (self.llm_api_base_url or self.llm_base_url).strip()

    @property
    def resolved_llm_timeout(self) -> float:
        return self.llm_timeout_seconds or self.llm_timeout


settings = Settings()
