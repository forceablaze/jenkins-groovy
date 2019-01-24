#!/bin/bash
cat /dev/stdin | awk '/^JSONSTRING:/ { print substr($0, 12)}'
