if [ -d app/build/outputs/apk/release ]; then
        if [ -n "$(ls -A app/build/outputs/apk/release)" ]; then
          cp app/build/outputs/apk/release/*.apk GitBuilds/AbleMusic-${1}-$(echo $TRIGGERING_SHA | cut -c1-8).apk
          cd GitBuilds
          curl --progress-bar -F document=@"AbleMusic-${1}-$(echo $TRIGGERING_SHA | cut -c1-8).apk" "https://api.telegram.org/bot${TG_BOT_KEY}/sendDocument" \
            -F chat_id="-1001415196670"  \
            -F "disable_web_page_preview=true" \
            -F "parse_mode=Markdown" \
            -F caption="\`$1 (cores: $(nproc --all))\`"
          rm -rf app/build/outputs/apk
	fi
fi
