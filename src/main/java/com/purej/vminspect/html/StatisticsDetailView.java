// Copyright (c), 2013, adopus consulting GmbH Switzerland, all rights reserved.
package com.purej.vminspect.html;

import java.io.IOException;
import com.purej.vminspect.data.statistics.Range;

/**
 * Displays the statistics detail page with one statistics.
 *
 * @author Stefan Mueller
 */
public final class StatisticsDetailView extends AbstractStatisticsView {
  private final String _statsName;
  private final int _statsWidth;
  private final int _statsHeight;

  /**
   * Creates a new instance of this view.
   */
  public StatisticsDetailView(StringBuilder output, Range range, String statsName, int statsWidth, int statsHeight) {
    super(output, range);
    _statsName = statsName;
    _statsWidth = statsWidth;
    _statsHeight = statsHeight;
  }

  @Override
  public void render() throws IOException {
    writeln("<h3>" + img("icons/charts-24.png", "Statistics") + "&nbsp;Statistics Detail</h3>");
    writeln("<div align='center'>");
    writeChoosePeriodLinks(_statsName, _statsWidth, _statsHeight);
    writeln("</div><br/>");
    String params = statisticsGraphParams(_statsName, _statsWidth, _statsHeight);
    writeln("<div align='center'>");
    writeln("<img class='synthese' id='img' src='?" + params + "' alt='zoom'/><br/><br/>");
    String paramsOut = addRangeParams(statisticsPageParams("statsDetail=" + _statsName, "statsWidth=" + (int) (_statsWidth / 1.5d), "statsHeight="
        + (int) (_statsHeight / 1.2)));
    String paramsIn = addRangeParams(statisticsPageParams("statsDetail=" + _statsName, "statsWidth=" + (int) (_statsWidth * 1.5d), "statsHeight="
        + (int) (_statsHeight * 1.2)));
    writeln(lnk(paramsOut, img("icons/zoom-out-24.png", "Zoom Out")));
    writeln(lnk(paramsIn, img("icons/zoom-in-24.png", "Zoom In")));
    writeln("</div>");
  }
}
