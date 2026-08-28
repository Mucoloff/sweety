package dev.sweety.media.platform.linux;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;

/** The subset of org.mpris.MediaPlayer2.Player the module drives. */
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
public interface MprisPlayerInterface extends DBusInterface {

    void PlayPause();

    void Play();

    void Pause();

    void Next();

    void Previous();

    /** @param offset relative move in microseconds */
    void Seek(long offset);

    /** @param position absolute position in microseconds */
    void SetPosition(DBusPath trackId, long position);
}
