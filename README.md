# α-Remote
Bluetooth remote for Sony cameras

A Bluetooth remote control app for Sony cameras that is immediately available in your phone's notification area as soon as you turn on your camera. This app is free and open source.

The app uses Android's companion app feature, meaning that it does not actively scan for your camera, but is only started by Android when the camera has been seen by the system - and the app's service is shut down as soon as it is turned off again. The remote control buttons are there when you need them, but no resources or screenspace are used otherwise.

<a href="https://www.buymeacoffee.com/there.oughta.be" target="_blank"><img src="https://cdn.buymeacoffee.com/buttons/v2/default-blue.png" alt="Buy Me A Coffee" height="47" width="174" ></a>

# How to get it

Bear with me, I am currently working on a proper release on F-Droid and Google Play as well as some documentation. On Google Play you can already try a [beta version](https://play.google.com/store/apps/details?id=org.staacks.alpharemote).

# Compatibility

This app should work with any camera that is compatible with Sonys small physical **Bluetooth** (not IR!) remote control.

So far, this has been confirmed for the following models:

ILCE-6400 (α6400), ILCE-7M3 (α7 III), ILCE-7RM3 (α7R III), ILCE-9 (α9), ZV-E10

It is expected to also work with the following models:

DSC-RX100M7, DSC-RX100M7G, ZV-1, ILCE-7M4, ILCE-7M4K, ILCE-7RM4A, ZV-E10, ZV-E10L, ILCE-1, ILCE-7C, ILCE-7CL, ILCE-7SM3, ILCE-9M2, ILCE-6100, ILCE-6100L ,ILCE-6100Y ,ILCE-6600, ILCE-6600M, ILCE-7RM4, ILCE-6400L, ILCE-6400M, ILCE-7M3, ILCE-7M3K, ILCE-7RM3, ILCE-9

Please let me know if your camera works if it has not yet been confirmed here.

# FAQ / Troubleshooting

## Features / Compatibiliy

<details>
  <summary>Does it work with my camera?</summary>
  
  If your camera is not on the compatibility list above, then I don't know either. Make sure if it has support for Bluetooth remotes in its settings and just try it. If it is not in the list above, please open an issue to let me know that it works or to try and figure out what is necessary to make it work.
</details>
<details>
  <summary>Can you implement feature xy?</summary>
  
  If it is something that can be done by blindly pressing the buttons that are supported by the remote (like an intervalometer or a timer for bulb mode), then yes. I don't want to clutter the app's interface, but let me know about your idea and we will see.
  
  If it is something that requires reliably moving to absolute settings (like focus bracketing), then it will probably not be possible in a practical way as I can only send button presses and guess how long to press them.
  
  If it requires other buttons or directly setting values (like controls for ISO, shutter speed, aperture etc.), then no, this is not possible via Bluetooth (at least with the protocol that I am aware of).

  Also the protocol only offers minimal status feedback from the camera: Focus state (acquired or not), shutter state (open or closed) and recording state. There is no way to get a preview of the image, transfer the image or just get the camera's settings.
</details>
<details>
  <summary>Is this app compatible with geotagging via Bluetooth?</summary>
  
  Unfortunately, no, at least at the moment. It is high on my todo list to implement geotagging, but right now it is not supported.
</details>

## Trigger behavior

<details>
  <summary>Why does "trigger once" take multiple pictures?</summary>
  
  "Trigger once" presses the shutter button all the way down and waits until the camera reports that the shutter is closed. When it receives that status report, it releases the shutter button immediately. Unfortunately, the feedback via Bluetooth is slower than most burst mode settings, so if your camera is set to burst mode, this will take several pictures before the shutter is released.
</details>
<details>
  <summary>Why does regular "shutter" not always take a picture?</summary>

  The simple "shutter" corresponds to your camera's shutter button. But unlike the physical button on your camera, a button in the app cannot be half-pressed to focus. In the notification area it is even worse: Here you cannot even hold the button. With the physical button you would half-hold it to focus, then press it all the way until you hear/feel the shutter and release it. If you tap the icon in the notification area it will just blindly go to fully pressed and then to fully released - either immediately or if you set it up with a "hold" duration, it will stay pressed for a moment. This works in manual focus or if you half-pressed the shutter through another button, so the camera already has acquired its focus. But if it has not focussed yet, it will probably not be able to do this in the short time the shutter is pressed. Check out the "Trigger once" and "Trigger on focus" buttons which wait for the shutter or for the focus, respectively, before they release the shutter button.
</details>

<details>
  <summary>Why does regular "shutter" not always take a picture?</summary>

  The simple "shutter" corresponds to your camera's shutter button. But unlike the physical button on your camera, a button in the app cannot be half-pressed to focus. In the notification area it is even worse: Here you cannot even hold the button. With the physical button you would half-hold it to focus, then press it all the way until you hear/feel the shutter and release it. If you tap the icon in the notification area it will just blindly go to fully pressed and then to fully released - either immediately or if you set it up with a "hold" duration, it will stay pressed for a moment. This works in manual focus or if you half-pressed the shutter through another button, so the camera already has acquired its focus. But if it has not focussed yet, it will probably not be able to do this in the short time the shutter is pressed. Check out the "Trigger once" and "Trigger on focus" buttons which wait for the shutter or for the focus, respectively, before they release the shutter button.
</details>
<details>
  <summary>What is the difference between "Shutter", "Trigger once" and "Trigger on focus"?</summary>

  "Shutter" just presses your shutter button all the way down. This is a good choice for manual focus or if your use the "focus" button (which is just equivalent to half-pressing the shutter button) to focus before pressing the shutter. In other cases (i.e. you need to autofocus first), the simple "Shutter" is probably not what you want as autofocussing may take a moment.

  "Trigger once" presses your shutter button all the way down and holds it until the app receives the status of an opened shutter from the camera. This works well with any focus mode as it simply holds the button until a picture is being taken. Unfortunately, this is not fast enough in burst mode and will almost always result in taking multiple pictures.

  "Trigger on focus" presses your shutter half down, waits to receive the focus-acquired status from your camera, and then shortly presses it down fully. This works well with burst mode and autofocus, but fails entirely without autofocus as it will never receive the focus-acquired status.

  Unfortunately, I am not aware of a method to know if the camera is in burst mode and if it is in MF or AF, so the app cannot pick the best option automatically. But since "Trigger once" works in all modes with the only downside of taking multiple pictures in burst mode (which is why you have burst mode enabled in the first place, isn't it?) I would recommend that one as a default for a selftimer button that should just work.
</details>

## Troubleshooting
<details>
  <summary>Button xy does not have the function it should have!</summary>

  The app can only send button presses, but not status commands. It can press the "AF ON" button, but if cannot tell the camera to go to manual focus. The virtual "AF ON" button will do whatever your physical button does. So, if it does something unexpected, this is either a quirk from your camera model, it is because you changed its function or it might be in that mode. (This especially gives the "C1" button a very special role as the most versatile one supported by the remote.)
</details>

<details>
  <summary>The app connects to my camera, but the buttons do not work!</summary>

  You probably only enabled Bluetooth, but not the "Bluetooth Remote". This is a separate setting in your camera's settings menu and on some cameras it is not even on the same page as the Bluetooth settings. Make sure you have enabled it, because otherwise your camera will allow a connection (for example for GPS), but it will not allow control.
</details>



# License

The code is open under the GNU General Public License v3.0 (see LICENSE file).

# Thanks

Thanks to [Coral](https://github.com/coral/freemote), [Greg Leeds](https://gregleeds.com/reverse-engineering-sony-camera-bluetooth/) and [Mark Kirschenbaum](https://gethypoxic.com/blogs/technical/sony-camera-ble-control-protocol-di-remote-control?srsltid=AfmBOoo9bOLHOZqLp0yAeUOamPNzfgljuiNszQWuB8CmNReazU0YLHZx) for their work on documenting Sony's Bluetooth communication for their articles and/or projects.

Also thanks to all the Redditors from [r/SonyAlpha](https://www.reddit.com/r/SonyAlpha/) for their testing and detailed feedback, in particular Massinissa / Ironfly74.
