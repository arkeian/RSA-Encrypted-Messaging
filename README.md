# RSA-Encrypted-Messaging

Build the Java 17 browser application:

```bash
./build-browser.sh
python3 -m http.server 8000 --directory dist
```

Then open `http://localhost:8000` in two independent browser sessions.

For networks that require TURN, define `RSA_CHAT_TURN_SERVERS` before the module script in `index.html`:

```html
<script>
    window.RSA_CHAT_TURN_SERVERS = [{
        urls: ["turn:your-turn-server.example:3478"],
        username: "username",
        credential: "credential"
    }];
</script>
```

Do not commit real TURN credentials to the repository.

Test in this order:

1. Wait for `Loading Java runtime...` and `Generating keys...` to complete.
2. Create a room in one session and open its invitation in the other.
3. Compare and confirm both RSA fingerprints.
4. Send encrypted Unicode and multi-block messages in both directions.
5. Confirm each outgoing message changes from `Sending` to `Delivered`.
6. Open `Show RSA procedure` and inspect the records for the actual messages.
