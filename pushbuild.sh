echo "pushing build..."
if [ -d app/build/outputs/apk/debug ]; then
        if [ -n "$(ls -A app/build/outputs/apk/debug)" ]; then
          mkdir ~/tmp
          cp app/build/outputs/apk/debug/*.apk ~/tmp/AbleMusic-arm64.apk
          git checkout builds
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
