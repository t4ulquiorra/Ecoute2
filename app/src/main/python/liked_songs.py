import yt_dlp
import json


def get_liked_songs(cookie_string: str) -> str:
    tracks = []
    try:
        opts = {
            'quiet': True,
            'no_warnings': True,
            'extract_flat': True,
            'http_headers': {'Cookie': cookie_string},
        }
        with yt_dlp.YoutubeDL(opts) as ydl:
            result = ydl.extract_info(
                'https://music.youtube.com/playlist?list=LM',
                download=False
            )
            if result and 'entries' in result:
                for entry in (result.get('entries') or []):
                    if entry and entry.get('id'):
                        tracks.append({
                            'id': entry['id'],
                            'title': entry.get('title', 'Unknown'),
                            'artist': entry.get('uploader') or entry.get('channel'),
                            'thumbnail': entry.get('thumbnail')
                        })
    except Exception as e:
        print(f"Error: {e}")
    return json.dumps(tracks)
