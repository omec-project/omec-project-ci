# SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: Apache-2.0
# Generates trend graph for ng40 functionality tests

print( "**********************************************************" )
print( "STEP 1: Data management." )
print( "**********************************************************" )

# Command line arguments are read. Args include the database credentials, test name, branch name, and the directory to output files.
print( "Reading commmand-line args." )
args <- commandArgs(trailingOnly=TRUE)

if (length(args) < 3){
    print("Usage: Rscript trend.R <config-file> <data-file-directory> <output-directory>")
    q(status=1)
}

print("Importing libraries.")
library(ggplot2)
library(ggrepel)
library(reshape2)
library(readr)
library(rjson)

config <- fromJSON(file = args[1])
config

outputDirectory <- args[3]

# Get list of files from data directory
fileList <- list.files(path=args[2], pattern="*.csv")
data <-
  do.call("rbind",
          lapply(fileList,
                 function(x)
                 read.csv(paste(args[2], x, sep=''),
                 stringsAsFactors = FALSE)))

# Use only latest x data determined from config file.
usableData <- tail(data, config$builds_to_show)

# Remove extraneous units from values
for (i in 1:nrow(usableData)) {
  for (j in 1:ncol(usableData)){
    usableData[i, j] <- gsub("[^0-9.-]", "", usableData[i, j])
  }
}

usableData

# **********************************************************
# STEP 2: Organize data.
# **********************************************************

print( "**********************************************************" )
print( "STEP 2: Organize Data." )
print( "**********************************************************" )

# Exclude "build" column in dataframe
usableData$THROUGHPUT <- round(as.numeric(usableData$THROUGHPUT), digits=2)
usableData$build <- as.numeric(usableData$build)
dataFrameThroughput <- melt(usableData[c("THROUGHPUT")])

# Rename column names in dataFrame
colnames(dataFrameThroughput) <- c("Status",
                                   "Quantity")

dataFrameThroughput$build <- usableData$build

# Adding a temporary iterative list to the dataFrame so that there are no gaps in-between build numbers.
dataFrameThroughput$iterative <- seq(0, nrow(usableData) - 1, by=1)
print(dataFrameThroughput)

usableData$MEAN.LATENCY <- round(as.numeric(usableData$MEAN.LATENCY), digits=2)
dataFrameLatency <- melt(usableData[c("MEAN.LATENCY")])

# Rename column names in dataFrame
colnames(dataFrameLatency) <- c("Status",
                                "Quantity")

dataFrameLatency$build <- usableData$build

# Adding a temporary iterative list to the dataFrame so that there are no gaps in-between build numbers.
dataFrameLatency$iterative <- seq(0, nrow(usableData) - 1, by=1)
dataFrameLatency

# **********************************************************
# STEP 3: Generate graphs.
# **********************************************************

print( "**********************************************************" )
print( "STEP 3: Generate Plot." )
print( "**********************************************************" )

# -------------------
# Main Plot Formatted
# -------------------

print( "Formatting main plot." )

mainPlotThroughput <- ggplot(data=dataFrameThroughput, aes(x=iterative,
                                                           y=Quantity,
                                                           color=Status))

mainPlotLatency <- ggplot(data=dataFrameLatency, aes(x=iterative,
                                                     y=Quantity,
                                                     color=Status))

# set the default text size of the graph.
theme_set( theme_grey( base_size = 26 ) )

# geom_ribbon is used so that there is a colored fill below the lines. These values shouldn't be changed.

throughputColor <- geom_ribbon( aes( ymin = 0,
                             ymax = Quantity ),
                             fill = "#CC33CC",
                             linetype = 0,
                             alpha = 0.04 )

latencyColor <- geom_ribbon( aes( ymin = 0,
                             ymax = Quantity ),
                             fill = "#FF9900",
                             linetype = 0,
                             alpha = 0.07 )
#
# plcColor <- geom_ribbon( aes( ymin = 0,
#                              ymax = planned_cases ),
#                              fill = "#3399FF",
#                              linetype = 0,
#                              alpha = 0.02 )

# X-axis config
xScaleConfig <- scale_x_continuous( breaks = dataFrameThroughput$iterative,
                                   label = dataFrameThroughput$build )
# Y-axis config
yScaleConfigThroughput <- scale_y_continuous( breaks = seq( 0, max( dataFrameThroughput$Quantity ),
                                   by = ceiling( max( dataFrameThroughput$Quantity ) / 10 ) ) )
yScaleConfigLatency <- scale_y_continuous( breaks = seq( 0, max( dataFrameLatency$Quantity ),
                                   by = ceiling( max( dataFrameLatency$Quantity ) / 10 ) ) )

# Axis labels
xLabel <- xlab(config$x_axis_title)
yLabel <- ylab( "Flows per Second" )

# Title of plot
title <- labs( title = paste(config$graph_title, " - Throughput", sep=""), subtitle = paste( "Last Updated: ", format( Sys.time(), "%b %d, %Y at %I:%M %p %Z" ), sep="" ) )

# Other theme options
theme <- theme( plot.title = element_text( hjust = 0.5, size = 32, face ='bold' ),
           axis.text.x = element_text( angle = 0, size = 14 ),
           legend.position = "bottom",
           legend.text = element_text( size = 22 ),
           legend.title = element_blank(),
           legend.key.size = unit( 1.5, 'lines' ),
           legend.direction = 'horizontal',
           plot.subtitle = element_text( size=16, hjust=1.0 ) )

# Wrap legend
wrapLegend <- guides( color = guide_legend( nrow = 1, byrow = TRUE ) )

# Colors for the lines
lineColors <- scale_color_manual( labels = c( "Throughput"),
                                  values=c( "#CC33CC") )

# Store plot configurations as 1 variable
fundamentalGraphData <- mainPlotThroughput +
                        # pacColor +
                        # plcColor +
                        throughputColor +
                        xScaleConfig +
                        yScaleConfigThroughput +
                        xLabel +
                        yLabel +
                        theme +
                        wrapLegend +
                        lineColors +
                        title

print( "Generating line plot." )

lineGraphFormat <- geom_line( size = 1.1 )
pointFormat <- geom_point( size = 4 )
pointLabel <- geom_text_repel(aes(label=Quantity),
                              size = 6)

result <- fundamentalGraphData +
           lineGraphFormat +
           pointFormat +
           pointLabel

imageWidth <- 21
imageHeight <- 9
imageDPI <- 200

print("Saving plot...")

ggsave( paste(outputDirectory, "/throughput.png", sep=""),
         width = imageWidth,
         height = imageHeight,
         dpi = imageDPI )

print("Success for Throughput")

title <- labs( title = paste(config$graph_title, " - Average Latency", sep=""), subtitle = paste( "Last Updated: ", format( Sys.time(), "%b %d, %Y at %I:%M %p %Z" ), sep="" ) )
yLabel <- ylab( "Latency (µsec)" )
lineColors <- scale_color_manual( labels = c( "Latency"),
                                  values=c( "#FF9900") )

# Store plot configurations as 1 variable
fundamentalGraphData <- mainPlotLatency +
                        latencyColor +
                        xScaleConfig +
                        yScaleConfigLatency +
                        xLabel +
                        yLabel +
                        theme +
                        wrapLegend +
                        lineColors +
                        title

result <- fundamentalGraphData +
           lineGraphFormat +
           pointFormat +
           pointLabel

imageWidth <- 21
imageHeight <- 9
imageDPI <- 200

print("Saving plot...")

ggsave( paste(outputDirectory, "/latency.png", sep=""),
         width = imageWidth,
         height = imageHeight,
         dpi = imageDPI )

print("Success for Latency")
