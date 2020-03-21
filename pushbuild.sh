if [ -d app/build/outputs/apk/debug ]; then
        if [ -n "$(ls -A app/build/outputs/apk/debug)" ]; then
          mkdir ~/tmp
          mv app/build/outputs/apk/debug/*.apk ~/tmp/AbleMusic-${1}.apk
          rm -rf app/build/outputs/apk
	  git clone https://github.com/uditkarode/AbleMusicPlayer -b builds
	  cd AbleMusicPlayer 
          mv ~/tmp/* .
          git add .
          git config --global user.name 'Bob The Builder'
          git config --global user.email 'bob@the.builder'
          git remote set-url origin "https://x-access-token:${GH_TOKEN}@github.com/${GH_REPO}"
          git commit -m "bot: build arch: ${1} <$(TZ=Asia/Kolkata date +'%Y%m%d %T')>"
          git push -u origin builds
	  cd ..
	  rm -rf AbleMusicPlayer
	  rm -rf ~/tmp
	fi
fi
