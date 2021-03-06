#!/usr/bin/env python
# -*- encoding: utf-8
"""Print log event messages from a CloudWatch log group.

Usage: print_log_events.py <LOG_GROUP_NAME> [--filter=<FILTER>] [--start=<START>] [--end=<END>]
       print_log_events.py -h --help

Examples:
  pipenv run ./print_log_events.py 'pd_jboss_access' --filter='[remote_host_name, remote_logical_username, remote_authenticated_user, timestamp, request, query_string, status_code, bytes, duration_milliseconds >= 6000, original_query_id, original_query_counter, original_query_timestamp, developer, developer_name, application_id, application, user_id, user, beneficiary_id]' --start='2019-07-30T00:01Z' | less  # view slow responses since a specific date & time (UTC)
  pipenv run ./print_log_events.py 'pd_jboss_access' --filter='[remote_host_name, remote_logical_username, remote_authenticated_user, timestamp, request, query_string, status_code = 500, bytes, duration_milliseconds, original_query_id, original_query_counter, original_query_timestamp, developer, developer_name, application_id, application, user_id, user, beneficiary_id]' --start='2019-07-30T00:01Z' | wc -l  # count server errors since a specific date & time (UTC)

Options:
  <LOG_GROUP_NAME>    Name of the CloudWatch log group.
  --filter=<FILTER>    Only print events that match this CloudWatch filter pattern.
  --start=<START>     Only print events with a timestamp after this time.
  --end=<END>         Only print events with a timestamp before this time.
  -h --help           Show this screen.

"""

import boto3
import docopt
import maya


def get_log_events(log_group, filter_pattern=None, start_time=None, end_time=None):
    """Generate all the log events from a CloudWatch group.

    :param log_group: Name of the CloudWatch log group.
    :param filter_pattern the CloudWatch filter pattern to restrict results to
    :param start_time: Only fetch events with a timestamp after this time.
        Expressed as the number of milliseconds after midnight Jan 1 1970.
    :param end_time: Only fetch events with a timestamp before this time.
        Expressed as the number of milliseconds after midnight Jan 1 1970.

    """
    client = boto3.client('logs')
    kwargs = {
        'logGroupName': log_group,
        'limit': 10000,
    }

    if filter_pattern is not None:
        kwargs['filterPattern'] = filter_pattern
    if start_time is not None:
        kwargs['startTime'] = start_time
    if end_time is not None:
        kwargs['endTime'] = end_time

    while True:
        resp = client.filter_log_events(**kwargs)
        yield from resp['events']
        try:
            kwargs['nextToken'] = resp['nextToken']
        except KeyError:
            break


def milliseconds_since_epoch(time_string):
    dt = maya.when(time_string)
    seconds = dt.epoch
    return seconds * 1000


if __name__ == '__main__':
    args = docopt.docopt(__doc__)

    log_group = args['<LOG_GROUP_NAME>']

    filter_pattern = args['--filter']

    if args['--start']:
        try:
            start_time = milliseconds_since_epoch(args['--start'])
        except ValueError:
            exit(f'Invalid datetime input as --start: {args["--start"]}')
    else:
        start_time = None

    if args['--end']:
        try:
            end_time = milliseconds_since_epoch(args['--end'])
        except ValueError:
            exit(f'Invalid datetime input as --end: {args["--end"]}')
    else:
        end_time = None

    logs = get_log_events(
        log_group=log_group,
        filter_pattern=filter_pattern,
        start_time=start_time,
        end_time=end_time
    )
    for event in logs:
        print(event['message'].rstrip())