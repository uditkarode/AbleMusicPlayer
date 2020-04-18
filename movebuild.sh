if [ -d app/build/outputs/apk/release ]; then
        if [ -n "$(ls -A app/build/outputs/apk/release)" ]; then
          cp app/build/outputs/apk/release/*.apk GitBuilds/AbleMusic-${1}-$(echo $GITHUB_SHA | cut -c1-8).apk
          rm -rf app/build/outputs/apk
	fi
fi
