# LeKiwi Remote

`LeKiwi Remote` is a standalone Android app for driving the LeKiwi mobile base from a phone.

The Android app is self-contained, but the Raspberry Pi
on the robot still needs a compatible `lerobot` checkout because the robot-side host process comes from `lerobot`. Please note that I worked with a lerobot checkout from 10/25. So I can't guarantee it will work with a newer version.

## What It Does

- Omnidirectional joystick driving for the base
- Hold-to-rotate left and right controls
- Slow, medium, and fast speed modes
- Editable robot IP and SSH settings
- Optional SSH button to start the LeKiwi host on the Raspberry Pi
- In-app debug console for SSH and ZMQ connection troubleshooting

The app sends base commands over ZMQ in this form:

```json
{"x.vel":0.1,"y.vel":0.0,"theta.vel":0.0}
```

## Project Layout

- `app/`: Android application code
- `patches/`: helper patch for the Raspberry Pi `lerobot` checkout
- `gradlew`, `gradle/`: Gradle wrapper so the project can be built independently

## Android Build

Open this folder directly in Android Studio:

```text
lekiwi-remote
```

Or build from the command line:

```bash
./gradlew assembleDebug
```

The debug APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Raspberry Pi Requirements

The robot still needs:

1. A working `lerobot` checkout on the Raspberry Pi
2. A Python environment that can run:

```bash
python -m lerobot.robots.lekiwi.lekiwi_host
```

For example a Conda env named `lerobot`.

## Required Robot-Side Patch

For this app, the Raspberry Pi host must accept base-only commands that do not include arm joint positions.

Use this patch on the Raspberry Pi `lerobot` checkout:

```bash
git apply /path/to/lekiwi-remote/patches/lekiwi_base_only_commands.patch
```

If the patch file is not in the Pi checkout, you can also apply the change manually. It simply makes
`LeKiwi.send_action()` skip the arm write when no arm targets are present.

## Recommended Host Start Command

For a Conda environment called `lerobot`, a good default remote command is:

```bash
nohup bash -lc 'source ~/miniconda3/etc/profile.d/conda.sh && conda activate lerobot && python -m lerobot.robots.lekiwi.lekiwi_host --robot.id=my_awesome_kiwi --host.connection_time_s=36000' >/tmp/lekiwi_host.log 2>&1 < /dev/null &
```

If your Conda install lives in `~/anaconda3` instead of `~/miniconda3`, update that path accordingly.

## App Setup

1. Make sure the phone and Raspberry Pi are on the same network.
2. Open the app settings.
3. Enter:
   - `Robot IP`: Raspberry Pi IP used for ZMQ driving
   - `Command port`: usually `5555`
   - `SSH host`: usually the same Raspberry Pi IP
   - `SSH port`: usually `22`
   - `SSH user`: your Raspberry Pi username
   - `SSH password`: your Raspberry Pi password
   - `Host start command`: the Conda-aware host command above
4. Save settings.
5. Tap `Start Host On Robot` if you want the phone to launch the host over SSH.
6. Drive with the joystick.

## Debugging

The app includes a `Debug Console` on the main screen. It shows:

- SSH connection attempts
- SSH command execution progress
- ZMQ endpoint setup
- ZMQ transport errors
- settings save events

On the Raspberry Pi, the recommended host command writes logs to:

```bash
/tmp/lekiwi_host.log
```

To inspect that log:

```bash
tail -f /tmp/lekiwi_host.log
```

## Independence From `lerobot`

This Android project can live in its own GitHub repo and be moved anywhere.

What remains coupled to `lerobot` is only the Raspberry Pi runtime:

- the host process implementation
- the required base-only command patch

## License

This folder currently includes the Apache 2.0 license text as `LICENSE`.
