echo "pushing build..."
if [ -d build/outputs/apk ]; then
        if [ -n "$(ls -A build/outputs/apk)" ]; then
          mkdir ~/tmp
          cp build/outputs/apk/*.apk ~/tmp
          git checkout -b builds
          rm *.apk -f
          cp ~/tmp/* .
          git add .
          git config --global user.name 'Bob The Builder'
          git config --global user.email 'bob@the.builder'
          git remote set-url origin "https://x-access-token:${GH_TOKEN}@github.com/${GH_REPO}"
          git commit -m "bot: build installables <$(TZ=Asia/Kolkata date +'%Y%m%d %T')>"
          git push -u origin builds
	fi
fi
