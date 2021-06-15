import git
import html
import os
import requests

github_event_before = os.getenv('GITHUB_EVENT_BEFORE')
github_sha = os.getenv('GITHUB_SHA')
github_repository = os.getenv('GITHUB_REPOSITORY')
telegram_ci_bot_token = os.getenv('TELEGRAM_CI_BOT_TOKEN')
telegram_ci_channel_id = os.getenv('TELEGRAM_CI_CHANNEL_ID')
branch = os.getenv('BRANCH')

repository = git.Repo('.')
commits_range = f'{github_event_before}...{github_sha}'
commits = list(repository.iter_commits(commits_range))

overview_link = f'https://github.com/{github_repository}/compare/{commits_range}'
overview_link_tag = f'''<a href="{overview_link}">{len(commits)} new commit{'s' if len(commits) > 1 else ''}</a>'''
message = f'''<b>🔨 {overview_link_tag} to <code>lawnchair:{branch}</code>:</b>\n'''

for commit in reversed(commits):
    commit_message = commit.message.split('\n')[0]
    commit_link = f'https://github.com/{github_repository}/commit/{commit.hexsha}'
    commit_link_tag = f'<a href="{commit_link}"><code>{repository.git.rev_parse(commit.hexsha, short=7)}</code></a>'
    encoded_message = html.escape(commit_message)
    message += f'\n• {commit_link_tag}: {encoded_message}'

data = {
    'chat_id': telegram_ci_channel_id,
    'parse_mode': 'HTML',
    'text': message,
    'disable_web_page_preview': 'true',
    'disable_notification': 'true'
}

r = requests.post(
    f'https://api.telegram.org/bot{telegram_ci_bot_token}/sendMessage', data)
