# SPDX-FileCopyrightText: 2020-present Open Networking Foundation <info@opennetworking.org>
# SPDX-License-Identifier: Apache-2.0
# Adds entry to csv file (used by Jenkins omec_nightly job)

print( "Reading commmand-line args." )
args <- commandArgs(trailingOnly=TRUE)

if (length(args) < 3){
    print("Usage: Rscript trend.R <key> <value> <filename>")
    q(status=1)
}

data <- read.csv(args[3], stringsAsFactors = FALSE)
data[args[1]] <- args[2]

write.csv(data, args[3], row.names = FALSE, quote = FALSE)
