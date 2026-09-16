# Run automated build and GitHub Release
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
python scripts/publish_release.py @args
