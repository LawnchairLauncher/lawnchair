import git
import html
import os
import requests
import re

github_event_before = os.getenv("GITHUB_EVENT_BEFORE")
github_sha = os.getenv("GITHUB_SHA")
github_repository = os.getenv("GITHUB_REPOSITORY")
github_ref = os.getenv("GITHUB_REF")

telegram_bot_token = os.getenv("TELEGRAM_BOT_TOKEN")
telegram_ci_channel_id = os.getenv("TELEGRAM_CI_CHANNEL_ID")
telegram_team_group_id = os.getenv("TELEGRAM_TEAM_GROUP_ID")
telegram_news_channel_id = os.getenv("TELEGRAM_NEWS_CHANNEL_ID")

artifact_directory = os.getenv("ARTIFACT_DIRECTORY")
action = os.getenv("ACTION")

def send_message_to_telegram_chat(chat_id, message, silent):
    requests.post(
        url = f"https://api.telegram.org/bot{telegram_bot_token}/sendMessage",
        data = {
           "chat_id": chat_id,
           "parse_mode": "HTML",
           "text": message,
           "disable_web_page_preview": "true",
           "disable_notification": str(silent)
        }
    )

def send_document_to_telegram_chat(chat_id, document):
    requests.post(
        url = f"https://api.telegram.org/bot{telegram_bot_token}/sendDocument",
        data = { "chat_id": chat_id },
        files = { "document": document }
    )

def send_artifact_to_telegram_chat(chat_id):
    with open(f"{artifact_directory}/{os.listdir(artifact_directory)[0]}", "rb") as artifact:
        send_document_to_telegram_chat(chat_id=chat_id, document=artifact)

def send_internal_notifications():
    repository = git.Repo(".")
    commit_range = f"{github_event_before}...{github_sha}"
    commits = list(repository.iter_commits(commit_range))

    if len(commits) == 0: return

    overview_link = f"https://github.com/{github_repository}/compare/{commit_range}"
    overview_link_tag = f"""<a href="{overview_link}">{len(commits)} new commit{"s" if len(commits) > 1 else ""}</a>"""
    message = f"""<b>🔨 {overview_link_tag} to <code>lawnchair:{github_ref}</code>:</b>\n"""

    for commit in reversed(commits):
        commit_message = commit.message.split("\n")[0]
        commit_link = f"https://github.com/{github_repository}/commit/{commit.hexsha}"
        commit_link_tag = f"""<a href="{commit_link}">{repository.git.rev_parse(commit.hexsha, short=7)}</a>"""
        encoded_message = html.escape(commit_message)
        message += f"\n• {commit_link_tag}: {encoded_message}"

    send_message_to_telegram_chat(chat_id=telegram_ci_channel_id, message=message, silent=False)
    send_message_to_telegram_chat(chat_id=telegram_team_group_id, message=message, silent=True)
    send_artifact_to_telegram_chat(chat_id=telegram_ci_channel_id)

def send_update_announcement():
    send_artifact_to_telegram_chat(chat_id=telegram_news_channel_id)
    
    with open("TELEGRAM_CHANGELOG.txt") as telegram_changelog:
        send_message_to_telegram_chat(chat_id=telegram_news_channel_id, message=telegram_changelog.read(), silent=False)

match action:
    case "internal_notifications":
        send_internal_notifications()
    case "update_announcement":
        send_update_announcement()
