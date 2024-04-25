/*
 *  Copyright (C) 2024  JNNGL
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.jnngl.vanillaminimaps.config;


import com.maximde.minimapapi.MinimapScreenPosition;
import net.elytrium.serializer.annotations.Comment;
import net.elytrium.serializer.annotations.CommentValue;
import net.elytrium.serializer.annotations.NewLine;
import net.elytrium.serializer.language.object.YamlSerializable;

public class Config extends YamlSerializable {

  private static final Config INSTANCE = new Config();

  public static Config instance() {
    return INSTANCE;
  }

  Config() {
  }

  public boolean enabledByDefault = true;

  @NewLine
  @Comment(@CommentValue("Available values: LEFT, RIGHT"))
  public MinimapScreenPosition defaultPosition = MinimapScreenPosition.LEFT;

  @NewLine
  @Comment(@CommentValue("Builtin renderers: vanilla, flat"))
  public String defaultMinimapRenderer = "vanilla";

  @NewLine
  public Markers markers = new Markers();

  public static class Markers {

    public DeathMarker deathMarker = new DeathMarker();

    public static class DeathMarker {

      public boolean enabled = true;

      public boolean stickToBorder = true;
    }

    public CustomMarkers customMarkers = new CustomMarkers();

    public static class CustomMarkers {

      public int limit = 5;

      public boolean stickToBorder = true;
    }
  }

  public Fullscreen fullscreen = new Fullscreen();

  public static class Fullscreen {

    @Comment(@CommentValue("1 segment = 128*128 pixels = 8*8 chunks"))
    public int segmentsX = 5;

    public int segmentsZ = 3;
  }
}
