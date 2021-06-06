import git
import os
import requests
import urllib

github_event_before = os.getenv('GITHUB_EVENT_BEFORE')
github_sha = os.getenv('GITHUB_SHA')
github_repo = os.getenv('GITHUB_REPO')
telegram_ci_bot_token = os.getenv('TELEGRAM_CI_BOT_TOKEN')
telegram_ci_channel_id = os.getenv('TELEGRAM_CI_CHANNEL_ID')
branch = os.getenv('BRANCH')

repository = git.Repo('.')
commits_range = f'{github_event_before}...{github_sha}'
commits = list(repository.iter_commits(commits_range))
message = f'''**🔨 [{len(commits)} new {'commit' if len(commits) == 1 else 'commits'}](https://github.com/{github_repo}/compare/{commits_range}) to `lawnchair:{branch}`:**\n'''

for commit in commits:
  commit_message = urllib.parse.quote(commit.message.split('\n')[0].replace('_', '\\_'), safe='')
  message += f'''\n• [{repository.git.rev_parse(commit.hexsha, short=7)}](https://github.com/LawnchairLauncher/lawnchair/commit/{commit.hexsha}): {commit_message}'''

requests.get(f'''https://api.telegram.org/bot{telegram_ci_bot_token}/sendMessage?chat_id={telegram_ci_channel_id}&parse_mode=Markdown&text={message}&disable_web_page_preview=true''')
